package com.catchtable.coupon.scheduler;

import com.catchtable.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpirationScheduler {

    private final CouponRepository couponRepository;
    private final Clock clock;

    /**
     * 5분 주기로 만료된 쿠폰을 EXPIRED 상태로 전환한다.
     * 벌크 UPDATE 단일 쿼리로 처리하므로 트랜잭션 부담이 작다.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireCoupons() {
        LocalDateTime now = LocalDateTime.now(clock);
        int affected = couponRepository.expireCoupons(now);

        if (affected > 0) {
            log.info("[쿠폰 만료] {}건 EXPIRED 처리 완료", affected);
        }
    }
}
