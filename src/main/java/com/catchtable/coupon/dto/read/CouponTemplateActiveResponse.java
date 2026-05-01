package com.catchtable.coupon.dto.read;

import com.catchtable.coupon.entity.CouponTemplate;

import java.time.LocalDateTime;

public record CouponTemplateActiveResponse(
        Long templateId,
        String couponName,
        Integer discountRate,
        Integer amount,
        Integer remain,
        LocalDateTime startedAt,
        LocalDateTime expiredAt
) {
    public static CouponTemplateActiveResponse from(CouponTemplate template) {
        return new CouponTemplateActiveResponse(
                template.getId(),
                template.getCouponName(),
                template.getDiscountRate(),
                template.getAmount(),
                template.getRemain(),
                template.getStartedAt(),
                template.getExpiredAt()
        );
    }
}
