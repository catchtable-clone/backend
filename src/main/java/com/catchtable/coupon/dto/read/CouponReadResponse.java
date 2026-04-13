package com.catchtable.coupon.dto.read;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.entity.CouponStatus;

import java.time.LocalDateTime;

public record CouponReadResponse(
        Long couponId,
        String couponName,
        Integer discountRate,
        CouponStatus status,
        LocalDateTime usedAt,
        LocalDateTime expiredAt
) {
    public static CouponReadResponse from(Coupon coupon) {
        return new CouponReadResponse(
                coupon.getId(),
                coupon.getCouponTemplate().getCouponName(),
                coupon.getCouponTemplate().getDiscountRate(),
                coupon.getStatus(),
                coupon.getUsedAt(),
                coupon.getCouponTemplate().getExpiredAt()
        );
    }
}
