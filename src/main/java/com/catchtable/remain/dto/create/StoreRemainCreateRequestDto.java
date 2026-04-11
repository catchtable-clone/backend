package com.catchtable.remain.dto.create;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StoreRemainCreateRequestDto(
        @NotNull(message = "매장 ID는 필수입니다.")
        Long storeId,

        @NotNull(message = "연도는 필수입니다.")
        @Min(value = 2024, message = "올바른 연도를 입력해주세요.")
        Integer year,

        @NotNull(message = "월은 필수입니다.")
        @Min(value = 1, message = "월은 1 이상이어야 합니다.")
        @Max(value = 12, message = "월은 12 이하여야 합니다.")
        Integer month
) {
}
