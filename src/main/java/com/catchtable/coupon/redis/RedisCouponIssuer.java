package com.catchtable.coupon.redis;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 선착순 쿠폰 발급의 원자 결정(재고/중복/차감/마킹)을 Redis Lua 로 처리한다.
 *
 * 격리:
 *  - Redis 호출은 동기. 1ms 미만으로 끝나는 가벼운 호출이라 별도 executor 가 오히려 비용.
 *  - 인프라 장애 격리는 @CircuitBreaker 만 사용. 일정 실패율 누적 시 회로 OPEN → 즉시 폴백.
 *  - 단일 명령 hang 보호는 Redisson 글로벌 timeout(기본 3초)에 위임.
 *  - 비즈니스 예외(DUPLICATE/EXHAUSTED)는 yaml 의 ignore-exceptions 로 회로 카운트에서 제외.
 *
 * 발급자 SET TTL 동기화:
 *  - 빈 SET 에 미리 EXPIREAT 을 거는 방식은 Redis 가 무시하므로 메모리 누수가 발생한다.
 *  - 따라서 Lua 스크립트가 SADD 직후 stock 키의 TTL 을 읽어 issued SET 에 동기화한다.
 *  - 자바 코드에서는 stock 키만 TTL 을 갖도록 관리하고 issued SET 의 TTL 은 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCouponIssuer {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String RESILIENCE_INSTANCE = "coupon-redis";

    private final RedissonClient redissonClient;

    private String issueLuaScript;

    @PostConstruct
    void loadScript() {
        try (var in = new ClassPathResource("lua/issue_coupon.lua").getInputStream()) {
            this.issueLuaScript = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("issue_coupon.lua 로드 실패", e);
        }
    }

    /**
     * 발급 시도. 동기 호출.
     * Redis 인프라 장애는 회로가 OPEN 되어 fallback 으로 UNAVAILABLE 반환.
     */
    @CircuitBreaker(name = RESILIENCE_INSTANCE, fallbackMethod = "tryIssueFallback")
    public IssueResult tryIssue(Long templateId, Long userId) {
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
    }

    /**
     * Circuit Breaker 폴백.
     * 비즈니스 예외는 ignore-exceptions 로 걸러져 여기엔 인프라 장애만 들어온다.
     */
    @SuppressWarnings("unused") // Resilience4j 가 리플렉션으로 호출
    private IssueResult tryIssueFallback(Long templateId, Long userId, Throwable t) {
        log.warn("쿠폰 발급 Redis 호출 실패. templateId={}, userId={}, cause={}",
                templateId, userId, t.toString());
        return IssueResult.UNAVAILABLE;
    }

    /**
     * Lua 통과 후 DB INSERT 가 실패한 경우의 보상.
     * stock 을 1 복구하고 발급자 SET 에서 userId 를 제거한다.
     *
     * 주의: best-effort. 보상 자체가 실패하면 재고 1건이 영구 소실될 수 있다.
     *      운영에서는 별도 알람 + 정합성 점검 잡으로 보완.
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
     * 템플릿 생성 직후 호출. stock 키만 만료시각까지 살려둔다.
     * issued SET 은 Lua 스크립트가 SADD 시점에 stock TTL 로 동기화한다.
     */
    public void warmUp(Long templateId, Integer remain, LocalDateTime expiredAt) {
        long ttlSeconds = ttlSeconds(expiredAt);
        var stock = redissonClient.getBucket(stockKey(templateId), StringCodec.INSTANCE);
        stock.set(String.valueOf(remain), Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Redis 가 보유한 현재 재고. 키가 없으면 null.
     * 워밍업 누락/만료 상황과 stock=0 을 호출자가 구분할 수 있도록 null 을 보존.
     */
    public Integer getStock(Long templateId) {
        var bucket = redissonClient.getBucket(stockKey(templateId), StringCodec.INSTANCE);
        Object value = bucket.get();
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("쿠폰 stock 키 값이 정수가 아님: templateId={}, value={}", templateId, value);
            return null;
        }
    }

    /**
     * 서버 부팅 시 호출. 이미 키가 있으면(Redis 운영 중 상태 유지 중) 건드리지 않는다.
     * Redis 콜드 스타트일 때만 DB 의 remain 으로 워밍업.
     */
    public void warmUpIfAbsent(Long templateId, Integer remain, LocalDateTime expiredAt) {
        long ttlSeconds = ttlSeconds(expiredAt);
        var stock = redissonClient.getBucket(stockKey(templateId), StringCodec.INSTANCE);
        boolean inserted = stock.setIfAbsent(String.valueOf(remain), Duration.ofSeconds(ttlSeconds));
        if (inserted) {
            log.info("Redis 콜드 스타트 워밍업: templateId={}, remain={}", templateId, remain);
        }
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
