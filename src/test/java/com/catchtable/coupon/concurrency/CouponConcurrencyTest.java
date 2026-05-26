package com.catchtable.coupon.concurrency;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.redis.RedisCouponIssuer;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 검증 (Redis Lua + 즉시 DB INSERT).
 *
 * 시나리오:
 *  - 잔여 STOCK 개를 CONCURRENT 명(CONCURRENT > STOCK)이 동시 발급 시도
 *  - Redis Lua 가 정확히 STOCK 건만 통과시켜야 한다 (재고 0 + 발급자 SET size = STOCK).
 *  - 통과한 요청은 trans안에서 coupons row 1건 INSERT 한다 → DB row count == STOCK.
 *  - 초과 요청은 EXHAUSTED 예외로 떨어진다.
 *
 * 전제:
 *  - docker compose -f docker-compose.dev.yml up -d (DB + Redis) 가 실행 중이어야 한다.
 *
 * 주의:
 *  - 클래스/메서드에 @Transactional 을 붙이면 안 된다.
 *    각 스레드가 독립 트랜잭션 안에서 발급해야 동시성 검증이 의미를 갖는다.
 */
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired private CouponService couponService;
    @Autowired private CouponTemplateRepository couponTemplateRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RedisCouponIssuer redisCouponIssuer;
    @Autowired private RedissonClient redissonClient;

    private CouponTemplate template;
    private List<User> users;

    private static final int STOCK = 100;       // 잔여 수량
    private static final int CONCURRENT = 300;  // 동시 요청 수

    @BeforeEach
    void setUp() {
        template = couponTemplateRepository.save(
                CouponTemplate.builder()
                        .couponName("동시성 테스트 쿠폰")
                        .discountRate(10)
                        .amount(STOCK)
                        .remain(STOCK)
                        .startedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(1))
                        .build()
        );

        // Redis 워밍업: 재고 카운터/발급자 SET 셋업.
        // 운영 경로는 createTemplate 안에서 자동 호출되지만, 테스트는 엔티티만 직접 save 했으므로 명시 호출.
        redisCouponIssuer.warmUp(template.getId(), template.getRemain(), template.getExpiredAt());

        long suffix = System.currentTimeMillis();
        users = new ArrayList<>();
        for (int i = 0; i < CONCURRENT; i++) {
            users.add(userRepository.save(
                    User.builder()
                            .email("concurrency-" + suffix + "-" + i + "@test.com")
                            .nickname("concurrency-" + suffix + "-" + i)
                            .googleId("concurrency-google-" + suffix + "-" + i)
                            .build()
            ));
        }
    }

    @AfterEach
    void cleanUp() {
        // 외래키 의존 순서: coupon → user / coupon_template
        // 운영 데이터 보호를 위해 deleteAllInBatch 절대 사용 금지. 테스트로 생성한 row 만 정리.
        List<Coupon> couponsToDelete = users.stream()
                .flatMap(u -> couponRepository.findAllByUserId(u.getId()).stream())
                .toList();
        couponRepository.deleteAll(couponsToDelete);
        userRepository.deleteAll(users);
        couponTemplateRepository.delete(template);

        // Redis 키 정리
        redissonClient.getBucket("coupon:stock:" + template.getId(), StringCodec.INSTANCE).delete();
        redissonClient.getSet("coupon:issued:" + template.getId(), StringCodec.INSTANCE).delete();
    }

    @Test
    @DisplayName("재고 100개 쿠폰을 300명이 동시 발급해도 정확히 100건만 성공한다")
    void issueCoupon_concurrent() throws InterruptedException {
        // 풀 크기 < CONCURRENT 면 큐에 대기 중인 task 가 ready.countDown 을 호출하지 못해
        // ready.await 가 영원히 풀리지 않는다. 풀 크기는 반드시 CONCURRENT 와 같아야 한다.
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT);
        CountDownLatch ready = new CountDownLatch(CONCURRENT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (User user : users) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    couponService.issueCoupon(template.getId(), user.getId());
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        boolean finished = done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("60초 안에 모든 스레드 종료").isTrue();
        assertThat(successCount.get()).as("성공 건수는 재고와 같아야 함").isEqualTo(STOCK);
        assertThat(failureCount.get()).as("실패 건수 = 초과 요청 수").isEqualTo(CONCURRENT - STOCK);

        // Redis 상태 검증
        String stock = (String) redissonClient
                .getBucket("coupon:stock:" + template.getId(), StringCodec.INSTANCE).get();
        assertThat(stock).as("Redis 재고 = 0").isEqualTo("0");

        int issuedSize = redissonClient
                .getSet("coupon:issued:" + template.getId(), StringCodec.INSTANCE).size();
        assertThat(issuedSize).as("Redis 발급자 SET size = 재고").isEqualTo(STOCK);

        // DB 상태 검증 — 테스트 user 한정
        long issuedToTestUsers = users.stream()
                .mapToLong(u -> couponRepository.findAllByUserId(u.getId()).size())
                .sum();
        assertThat(issuedToTestUsers).as("DB coupons row = 재고").isEqualTo(STOCK);
    }
}
