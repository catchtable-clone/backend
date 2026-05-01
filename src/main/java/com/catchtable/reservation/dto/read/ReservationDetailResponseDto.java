package com.catchtable.reservation.dto.read;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationDetailResponseDto(
        Long id,
        String status,
        Integer member,
        StoreInfo store,
        RemainInfo remain,
        LocalDateTime createdAt
) {
    public record StoreInfo(
            Long storeId,
            String storeName,
            String storeImage,
            String storeCategory,
            String address
    ) {}

    public record RemainInfo(
            Long remainId,
            LocalDate remainDate,
            LocalTime remainTime
    ) {}
}
