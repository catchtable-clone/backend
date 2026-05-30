package com.catchtable.coupon.repository;

import com.catchtable.coupon.entity.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {

    // 활성 시간대(시작 도래 + 만료 전)의 템플릿.
    // 잔여 재고는 Redis 가 진실 원천이므로 DB remain 필터는 사용하지 않는다.
    // 호출자(CouponService)가 Redis stock 으로 필터링하고 스케줄러가 주기 sync 한다.
    @Query("""
            SELECT ct FROM CouponTemplate ct
            WHERE ct.isDeleted = false
              AND ct.startedAt <= :now
              AND ct.expiredAt >= :now
            ORDER BY ct.expiredAt ASC
            """)
    List<CouponTemplate> findActiveTemplates(@Param("now") LocalDateTime now);
}
