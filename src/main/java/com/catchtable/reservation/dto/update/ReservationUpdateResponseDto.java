package com.catchtable.reservation.dto.update;

import java.time.LocalDateTime;

public record ReservationUpdateResponseDto(
        Long id,
        Long remainId,
        Integer member,
        String status,
        LocalDateTime updatedAt
) {
}
