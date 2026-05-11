package com.catchtable.coupon.repository;

import com.catchtable.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c JOIN FETCH c.couponTemplate WHERE c.user.id = :userId AND c.isDeleted = false")
    List<Coupon> findAllByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);

    /**
     * UNUSED 상태의 쿠폰 중 템플릿 만료 시각이 지난 것을 일괄 EXPIRED로 전환한다.
     * JPQL bulk UPDATE는 JOIN 직접 불가하므로 서브쿼리로 매칭한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = com.catchtable.coupon.entity.CouponStatus.EXPIRED " +
            "WHERE c.status = com.catchtable.coupon.entity.CouponStatus.UNUSED " +
            "AND c.isDeleted = false " +
            "AND c.id IN (SELECT c2.id FROM Coupon c2 WHERE c2.couponTemplate.expiredAt < :now)")
    int expireCoupons(@Param("now") LocalDateTime now);
}
