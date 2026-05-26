package com.catchtable.coupon.redis;

import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서버 부팅 시 발급 가능한 쿠폰 템플릿의 Redis 상태를 점검·복원한다.
 *
 * 시나리오:
 *  - Redis 가 재기동되어 coupon:stock:{id} 키가 사라진 경우,
 *    DB 의 remain 값으로 워밍업하지 않으면 Lua 가 NOT_AVAILABLE 을 반환한다.
 *  - DB 에는 멀쩡한 템플릿이 있는데 발급 API 가 안 되는 운영 사고로 직결됨.
 *
 * 안전장치:
 *  - setIfAbsent 의미라 Redis 가 정상이고 운영 중 차감된 값이 있으면 덮어쓰지 않는다.
 *  - 따라서 평시 부팅에는 no-op, Redis 콜드 스타트일 때만 실효.
 *
 * 한계:
 *  - "Redis 가 죽은 동안 발급된 건수"는 복원 불가. Redis HA + AOF 영속화가 1차 방어선이다.
 */
@Slf4j
@Component
@Order(Integer.MAX_VALUE) // 다른 ApplicationRunner 이후 실행
@RequiredArgsConstructor
public class CouponWarmupRunner implements ApplicationRunner {

    private final CouponTemplateRepository couponTemplateRepository;
    private final RedisCouponIssuer redisCouponIssuer;

    @Override
    public void run(ApplicationArguments args) {
        List<CouponTemplate> activeTemplates = couponTemplateRepository.findActiveTemplates(LocalDateTime.now());
        if (activeTemplates.isEmpty()) {
            log.info("쿠폰 Redis 워밍업: 활성 템플릿 없음, 스킵");
            return;
        }

        for (CouponTemplate t : activeTemplates) {
            try {
                redisCouponIssuer.warmUpIfAbsent(t.getId(), t.getRemain(), t.getExpiredAt());
            } catch (Exception e) {
                // 한 템플릿 실패가 다른 템플릿 워밍업을 막지 않게 한다.
                log.error("쿠폰 Redis 워밍업 실패: templateId={}", t.getId(), e);
            }
        }
        log.info("쿠폰 Redis 워밍업 완료: 활성 템플릿 {}건 점검", activeTemplates.size());
    }
}
