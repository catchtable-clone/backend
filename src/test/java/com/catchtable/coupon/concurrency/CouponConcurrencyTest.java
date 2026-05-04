package com.catchtable.coupon.concurrency;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponTemplate;
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
 * 쿠폰 발급 비관적 락(PESSIMISTIC_WRITE) 동시성 검증.
 * - 잔여 N개 쿠폰을 더 많은 사용자(M명, M > N)가 동시에 발급 시도
 * - 락이 정상 동작하면 정확히 N건만 성공해야 한다.
 * - 실패한 요청은 CouponTemplate.decreaseRemain() 에서 COUPON_EXHAUSTED 예외를 던진다.
 *
 * 주의:
 *  - @Transactional 을 클래스/메서드에 붙이면 안 된다.
 *    각 스레드가 별도 트랜잭션 안에서 락을 잡아야 검증이 가능하다.
 *  - 테스트 종료 후 cleanup() 으로 직접 데이터를 지운다.
 */
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    private CouponTemplate template;
    private List<User> users;

    private static final int STOCK = 5;       // 잔여 수량
    private static final int CONCURRENT = 10; // 동시 요청 수

    @BeforeEach
    void setUp() {
        // 쿠폰 템플릿 (잔여 5개)
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

        // 사용자 10명 (각자 unique email/nickname/googleId)
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
        // 테스트로 생성된 쿠폰만 정리 (운영 데이터 보호 — deleteAllInBatch 절대 금지).
        // 외래키 의존 순서: coupon → user / coupon_template
        List<Coupon> couponsToDelete = users.stream()
                .flatMap(u -> couponRepository.findAllByUserId(u.getId()).stream())
                .toList();
        couponRepository.deleteAll(couponsToDelete);
        userRepository.deleteAll(users);
        couponTemplateRepository.delete(template);
    }

    @Test
    @DisplayName("재고 5개 쿠폰을 10명이 동시 발급해도 정확히 5건만 성공한다 (비관적 락 검증)")
    void issueCoupon_concurrent() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT);
        CountDownLatch ready = new CountDownLatch(CONCURRENT); // 모든 스레드 준비 완료 대기
        CountDownLatch start = new CountDownLatch(1);          // 동시 시작 트리거
        CountDownLatch done = new CountDownLatch(CONCURRENT);  // 모든 스레드 종료 대기

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (User user : users) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();                                   // 동시 발사
                    couponService.issueCoupon(template.getId(), user.getId());
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    // COUPON_EXHAUSTED 등 비즈니스 예외는 실패로 카운트
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // 락/JPA 충돌 등 예상 외 예외도 실패로 묶어서 가시화
                    failureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();              // 모든 스레드가 start 대기 상태가 될 때까지 기다림
        start.countDown();          // 동시 시작
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("30초 안에 모든 스레드 종료").isTrue();
        assertThat(successCount.get()).as("성공 건수는 재고와 같아야 함").isEqualTo(STOCK);
        assertThat(failureCount.get()).as("실패 건수").isEqualTo(CONCURRENT - STOCK);

        // DB 상태 검증
        CouponTemplate refreshed = couponTemplateRepository.findById(template.getId()).orElseThrow();
        assertThat(refreshed.getRemain()).as("템플릿 잔여 = 0").isZero();

        // 우리가 만든 user들 기준으로만 카운트 (운영 데이터 영향 배제)
        long issuedToTestUsers = users.stream()
                .mapToLong(u -> couponRepository.findAllByUserId(u.getId()).size())
                .sum();
        assertThat(issuedToTestUsers).as("테스트 user들에게 발급된 쿠폰 = 재고").isEqualTo(STOCK);
    }
}
