package com.catchtable.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReservationReminderEvent {
    private final Long reservationId;
    private final Long userId;
    private final String storeName;
    private final String remainDate;
    private final String remainTime;
}
