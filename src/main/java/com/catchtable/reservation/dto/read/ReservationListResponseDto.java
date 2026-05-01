package com.catchtable.reservation.dto.read;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationListResponseDto(
        Long id,
        Long remainId,
        String status,
        Long storeId,
        String storeName,
        String storeImage,
        String storeCategory,
        LocalDate remainDate,
        LocalTime remainTime,
        Integer member,
        LocalDateTime createdAt
) {
}
