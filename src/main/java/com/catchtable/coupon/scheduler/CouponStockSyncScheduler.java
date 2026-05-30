package com.catchtable.coupon.scheduler;

import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.redis.RedisCouponIssuer;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis stock → DB coupon_templates.remain 주기 동기화.
 *
 * 발급 트랜잭션은 hot row contention 회피를 위해 DB remain 을 건드리지 않고
 * Redis 가 단일 진실원 역할을 한다. 관리/통계/콜드 스타트 워밍업이 보는
 * DB remain 을 SoT 와 일치시키기 위해 본 잡이 백그라운드에서 sync.
 *
 * Redis 키가 만료/소실된 템플릿(stock == null)은 건드리지 않는다.
 * 워밍업 책임은 CouponWarmupRunner 에 있고, 본 잡이 0 으로 덮으면 정합성이 깨진다.
 *
 * 잡 자체 실패는 다음 주기에 재시도되므로 별도 재시도 큐 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockSyncScheduler {

    private static final long SYNC_INTERVAL_MS = 5_000L;

    private final CouponTemplateRepository couponTemplateRepository;
    private final RedisCouponIssuer redisCouponIssuer;

    @Scheduled(fixedDelay = SYNC_INTERVAL_MS)
    @Transactional
    public void syncStock() {
        List<CouponTemplate> templates = couponTemplateRepository.findActiveTemplates(LocalDateTime.now());
        for (CouponTemplate template : templates) {
            try {
                Integer stock = redisCouponIssuer.getStock(template.getId());
                if (stock == null) continue;
                if (!stock.equals(template.getRemain())) {
                    template.syncRemain(stock);
                }
            } catch (Exception e) {
                log.warn("쿠폰 stock sync 실패. templateId={}", template.getId(), e);
            }
        }
    }
}
