package com.catchtable.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReservationChangedEvent {
    private final Long newReservationId;
    private final Long userId;
    private final String storeName;
    private final String oldRemainDate;
    private final String oldRemainTime;
    private final String newRemainDate;
    private final String newRemainTime;
}
