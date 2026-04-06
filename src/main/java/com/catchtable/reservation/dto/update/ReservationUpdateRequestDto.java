package com.catchtable.reservation.dto.update;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationUpdateRequestDto(

        @NotNull(message = "변경할 예약 시간대(remainId)는 필수입니다.")
        Long newRemainId,

        @NotNull(message = "변경할 예약 인원 수는 필수입니다.")
        @Min(value = 1, message = "예약 인원은 1명 이상이어야 합니다.")
        Integer newMember
) {
}
