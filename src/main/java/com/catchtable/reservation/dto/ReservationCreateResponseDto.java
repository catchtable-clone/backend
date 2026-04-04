package com.catchtable.reservation.dto;

import com.catchtable.reservation.entity.ReservationStatus;

public record ReservationCreateResponseDto(
        Long reservationId,
        
        ReservationStatus status
) {
}
