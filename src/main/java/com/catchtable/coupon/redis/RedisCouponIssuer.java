package com.catchtable.coupon.redis;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 선착순 쿠폰 발급의 원자 결정(재고/중복/차감/마킹)을 Redis Lua로 처리한다.
 *
 * 격리(C번 항목):
 *  - Redisson 클라이언트는 예약 분산 락 등 다른 도메인과 공유한다.
 *    그래서 글로벌 타임아웃을 줄이는 대신, 쿠폰 발급 호출 메서드에만
 *    @TimeLimiter + @CircuitBreaker 를 걸어 다른 Redis 사용처에 영향이 가지 않게 한다.
 *  - Redis 장애가 일정 비율 넘으면 회로가 OPEN 으로 가서 호출 자체가 차단되고
 *    fallback 이 503(COUPON_ISSUE_TEMPORARILY_UNAVAILABLE) 으로 떨어뜨린다.
 *  - 비즈니스 예외(DUPLICATE/EXHAUSTED)는 yaml 의 ignore-exceptions 로 회로 카운트에서 제외.
 */
@Slf4j
@Component
public class RedisCouponIssuer {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String RESILIENCE_INSTANCE = "coupon-redis";

    private final RedissonClient redissonClient;
    private final Executor couponIssueExecutor;

    private String issueLuaScript;

    public RedisCouponIssuer(
            RedissonClient redissonClient,
            @Qualifier("couponIssueExecutor") Executor couponIssueExecutor) {
        this.redissonClient = redissonClient;
        this.couponIssueExecutor = couponIssueExecutor;
    }

    @PostConstruct
    void loadScript() {
        try (var in = new ClassPathResource("lua/issue_coupon.lua").getInputStream()) {
            this.issueLuaScript = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("issue_coupon.lua 로드 실패", e);
        }
    }

    /**
     * 발급 시도 (비동기 시그니처는 Resilience4j 어노테이션 요구사항).
     * CouponService 에서 .join() 으로 unwrap 하여 사용한다.
     */
    @TimeLimiter(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "tryIssueFallback")
    public CompletableFuture<IssueResult> tryIssue(Long templateId, Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            Long code = redissonClient
                    .getScript(StringCodec.INSTANCE)
                    .eval(
                            RScript.Mode.READ_WRITE,
                            issueLuaScript,
                            RScript.ReturnType.INTEGER,
                            List.of(stockKey(templateId), issuedKey(templateId)),
                            String.valueOf(userId)
                    );
            return IssueResult.of(code);
        }, couponIssueExecutor);
    }

    /**
     * Circuit Breaker / TimeLimiter 폴백.
     * 비즈니스 예외는 ignore-exceptions 로 걸러져서 여기엔 인프라 장애만 들어온다.
     */
    @SuppressWarnings("unused") // Resilience4j 가 리플렉션으로 호출
    private CompletableFuture<IssueResult> tryIssueFallback(Long templateId, Long userId, Throwable t) {
        log.warn("쿠폰 발급 Redis 호출 실패. templateId={}, userId={}, cause={}",
                templateId, userId, t.toString());
        return CompletableFuture.completedFuture(IssueResult.UNAVAILABLE);
    }

    /**
     * Lua 통과 후 DB INSERT 가 실패한 경우의 보상.
     * stock 을 1 복구하고 발급자 SET 에서 userId 를 제거한다.
     *
     * 주의: 이 보상은 best-effort 다. 보상 자체가 실패하면 재고 1건이 영구 소실될 수 있다.
     *      운영에서는 별도 알람 + 정합성 점검 잡으로 보완해야 한다.
     *      회로 차단 대상에서 제외 — 보상 실패가 회로를 더 빨리 열어버리면 운영상 손해.
     */
    public void compensate(Long templateId, Long userId) {
        redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        "if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('INCR', KEYS[1]) end; " +
                                "redis.call('SREM', KEYS[2], ARGV[1]); return 1",
                        RScript.ReturnType.INTEGER,
                        List.of(stockKey(templateId), issuedKey(templateId)),
                        String.valueOf(userId)
                );
    }

    /**
     * 템플릿 생성 직후 호출. 재고 카운터와 발급자 SET 을 만료시각까지 살려둔다.
     * 이미 키가 존재해도 덮어쓴다 — createTemplate 흐름에서만 사용해야 한다.
     */
    public void warmUp(Long templateId, Integer remain, LocalDateTime expiredAt) {
        long ttlSeconds = ttlSeconds(expiredAt);
        var stock = redissonClient.getBucket(stockKey(templateId), StringCodec.INSTANCE);
        stock.set(String.valueOf(remain), Duration.ofSeconds(ttlSeconds));

        Date expireAtDate = Date.from(expiredAt.atZone(ZoneId.systemDefault()).toInstant());
        redissonClient.getSet(issuedKey(templateId), StringCodec.INSTANCE).expireAt(expireAtDate);
    }

    /**
     * 서버 부팅 시 호출. 이미 키가 있으면(=Redis 가 운영 중 상태 유지 중) 건드리지 않는다.
     * Redis 가 재기동되어 키가 사라진 경우에만 DB 의 remain 으로 워밍업한다.
     *
     * 주의: "Redis 가 죽어있는 동안 발급된 건수"를 복원할 수는 없다.
     *      Redis HA(Sentinel/Cluster) + 영속화 설정이 1차 방어선이고,
     *      여기는 콜드 스타트 때만 의미 있는 fail-safe.
     */
    public void warmUpIfAbsent(Long templateId, Integer remain, LocalDateTime expiredAt) {
        long ttlSeconds = ttlSeconds(expiredAt);
        var stock = redissonClient.getBucket(stockKey(templateId), StringCodec.INSTANCE);
        boolean inserted = stock.setIfAbsent(String.valueOf(remain), Duration.ofSeconds(ttlSeconds));
        if (inserted) {
            log.info("Redis 콜드 스타트 워밍업: templateId={}, remain={}", templateId, remain);
        }
        // 발급자 SET 은 비어있어도 무방. TTL 만 보장.
        Date expireAtDate = Date.from(expiredAt.atZone(ZoneId.systemDefault()).toInstant());
        redissonClient.getSet(issuedKey(templateId), StringCodec.INSTANCE).expireAt(expireAtDate);
    }

    private static long ttlSeconds(LocalDateTime expiredAt) {
        return Math.max(Duration.between(LocalDateTime.now(), expiredAt).getSeconds(), 60L);
    }

    private static String stockKey(Long templateId) {
        return STOCK_KEY_PREFIX + templateId;
    }

    private static String issuedKey(Long templateId) {
        return ISSUED_KEY_PREFIX + templateId;
    }

    public enum IssueResult {
        SUCCESS, DUPLICATE, EXHAUSTED, NOT_AVAILABLE, UNAVAILABLE;

        static IssueResult of(Long code) {
            if (code == null) return NOT_AVAILABLE;
            return switch (code.intValue()) {
                case 1 -> SUCCESS;
                case 0 -> DUPLICATE;
                case -1 -> EXHAUSTED;
                default -> NOT_AVAILABLE;
            };
        }
    }
}
