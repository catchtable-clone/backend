package com.catchtable.coupon.dto.create;

import com.catchtable.coupon.entity.CouponTemplate;

public record CouponTemplateCreateResponse(
        Long templateId,
        String couponName,
        Integer discountRate,
        Integer amount,
        String startedAt,
        String expiredAt
) {
    public static CouponTemplateCreateResponse from(CouponTemplate template) {
        return new CouponTemplateCreateResponse(
                template.getId(),
                template.getCouponName(),
                template.getDiscountRate(),
                template.getAmount(),
                template.getStartedAt().toString(),
                template.getExpiredAt().toString()
        );
    }
}
