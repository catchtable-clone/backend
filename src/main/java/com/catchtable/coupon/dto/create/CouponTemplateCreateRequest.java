package com.catchtable.coupon.dto.create;

import com.catchtable.coupon.entity.CouponTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CouponTemplateCreateRequest(
        @NotBlank String couponName,
        @NotNull @Positive Integer discountRate,
        @NotNull @Positive Integer amount,
        @NotNull LocalDateTime startedAt,
        @NotNull LocalDateTime expiredAt
) {
    public CouponTemplate toEntity() {
        return CouponTemplate.builder()
                .couponName(couponName)
                .discountRate(discountRate)
                .amount(amount)
                .remain(amount)
                .startedAt(startedAt)
                .expiredAt(expiredAt)
                .build();
    }
}
