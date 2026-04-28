package com.catchtable.notification.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class VacancyEvent {
    private final Long remainId;
}
