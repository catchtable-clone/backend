package com.catchtable.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationVisitedEvent {
    private Long reservationId;
    private Long userId;
    private String storeName;
    private String remainDate;
    private String remainTime;
}
