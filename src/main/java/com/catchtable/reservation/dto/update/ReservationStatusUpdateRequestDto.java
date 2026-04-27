package com.catchtable.reservation.dto.update;

import com.catchtable.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.NotNull;

public record ReservationStatusUpdateRequestDto(
        @NotNull(message = "변경할 상태 값은 필수입니다.")
        ReservationStatus status
) {
}
