package com.catchtable.coupon.repository;

import com.catchtable.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c JOIN FETCH c.couponTemplate WHERE c.user.id = :userId AND c.isDeleted = false")
    List<Coupon> findAllByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
