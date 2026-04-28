package com.catchtable.notification.event;

import com.catchtable.remain.entity.StoreRemain;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class VacancyEvent {
    private final StoreRemain storeRemain;
}
