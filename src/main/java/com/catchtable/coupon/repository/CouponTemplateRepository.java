package com.catchtable.coupon.repository;

import com.catchtable.coupon.entity.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {

    // 현재 발급 가능한 쿠폰 템플릿 (시작 시각 도래 + 만료 전 + 잔여 수량 > 0)
    // 정렬: 곧 만료되는 것 우선
    @Query("""
            SELECT ct FROM CouponTemplate ct
            WHERE ct.isDeleted = false
              AND ct.startedAt <= :now
              AND ct.expiredAt >= :now
              AND ct.remain > 0
            ORDER BY ct.expiredAt ASC
            """)
    List<CouponTemplate> findActiveTemplates(@Param("now") LocalDateTime now);
}
