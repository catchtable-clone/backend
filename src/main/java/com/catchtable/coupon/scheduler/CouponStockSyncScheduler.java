package com.catchtable.coupon.scheduler;

import com.catchtable.coupon.entity.CouponTemplate;
import com.catchtable.coupon.redis.RedisCouponIssuer;
import com.catchtable.coupon.repository.CouponTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis stock → DB coupon_templates.remain 주기 동기화.
 *
 * 발급은 Redis 가 단일 진실원이라 DB remain 을 건드리지 않는다. 관리/통계 조회와
 * Redis 콜드 스타트 워밍업이 보는 DB 값을 Redis 와 맞추기 위해 본 잡이 sync.
 *
 * 함정:
 *  - Redis 키 만료/소실 시(stock == null) DB 를 0 으로 덮지 않는다. 워밍업이
 *    DB remain 으로 Redis 를 다시 채우는 책임을 가지므로 0 으로 덮으면 정합성이 깨진다.
 *  - 메서드에 @Transactional 을 걸지 않는다. 루프 안의 Redis I/O 동안 DB 커넥션이
 *    점유되어 HikariCP 풀이 압박을 받는다. UPDATE 가 필요한 템플릿만 좁게 묶는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStockSyncScheduler {

    private static final long SYNC_INTERVAL_MS = 5_000L;
    private static final String SYNC_LOCK_KEY = "lock:coupon:sync";
    private static final long LOCK_LEASE_SECONDS = 4L;

    private final CouponTemplateRepository couponTemplateRepository;
    private final RedisCouponIssuer redisCouponIssuer;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = SYNC_INTERVAL_MS)
    public void syncStock() {
        RLock lock = redissonClient.getLock(SYNC_LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) return;
            doSync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("쿠폰 stock sync 락 획득 인터럽트", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doSync() {
        List<CouponTemplate> templates = couponTemplateRepository.findActiveTemplates(LocalDateTime.now());
        for (CouponTemplate template : templates) {
            try {
                Integer stock = redisCouponIssuer.getStock(template.getId());
                if (stock == null) continue;
                if (stock.equals(template.getRemain())) continue;
                transactionTemplate.executeWithoutResult(status ->
                        couponTemplateRepository.findById(template.getId())
                                .ifPresent(t -> t.syncRemain(stock))
                );
            } catch (Exception e) {
                log.warn("쿠폰 stock sync 실패. templateId={}", template.getId(), e);
            }
        }
    }
}
