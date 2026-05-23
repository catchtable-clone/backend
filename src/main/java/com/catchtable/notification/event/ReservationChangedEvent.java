package com.catchtable.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationChangedEvent {
    private Long newReservationId;
    private Long userId;
    private String storeName;
    private String oldRemainDate;
    private String oldRemainTime;
    private String newRemainDate;
    private String newRemainTime;
}
