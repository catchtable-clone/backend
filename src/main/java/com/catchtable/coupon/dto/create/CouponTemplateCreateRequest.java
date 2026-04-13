package com.catchtable.coupon.dto.create;

import com.catchtable.coupon.entity.CouponTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CouponTemplateCreateRequest {

    @NotBlank
    private String couponName;

    @NotNull
    @Positive
    private Integer discountRate;

    @NotNull
    @Positive
    private Integer amount;

    @NotNull
    private LocalDateTime startedAt;

    @NotNull
    private LocalDateTime expiredAt;

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
