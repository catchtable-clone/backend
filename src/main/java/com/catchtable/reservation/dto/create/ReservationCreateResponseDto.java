package com.catchtable.reservation.dto.create;

import com.catchtable.reservation.entity.ReservationStatus;

public record ReservationCreateResponseDto(
        Long id,
        ReservationStatus status
) {
}
