package com.catchtable.reservation.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequestDto(
        
        @Schema(example = "1")
        @NotNull(message = "예약 가능한 시간 선택은 필수입니다.")
        Long remainId,
        
        @Schema(example = "1")
        @NotNull(message = "예약자 아이디는 필수입니다.")
        Long userId,
                
        @Schema(example = "4")
        @NotNull(message = "예약 인원은 필수입니다.")
        @Min(value = 1, message = "예약 인원은 1 이상이어야 합니다.")
        Integer member,

        @Schema(example = "1", description = "쿠폰 ID (선택)")
        Long couponId
) {
}
