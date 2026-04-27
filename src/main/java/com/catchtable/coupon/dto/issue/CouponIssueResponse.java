package com.catchtable.coupon.dto.issue;

import com.catchtable.coupon.entity.Coupon;

public record CouponIssueResponse(
        Long couponId,
        String couponName,
        Integer discountRate
) {
    public static CouponIssueResponse from(Coupon coupon) {
        return new CouponIssueResponse(
                coupon.getId(),
                coupon.getCouponTemplate().getCouponName(),
                coupon.getCouponTemplate().getDiscountRate()
        );
    }
}
