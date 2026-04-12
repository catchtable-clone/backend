package com.catchtable.vacancy.dto.write;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.store.entity.Store;
import com.catchtable.vacancy.entity.Vacancy;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
public class VacancyListResponse {

    private final Long vacancyId;
    private final Long remainId;
    private final String storeName;
    private final LocalDate remainDate;
    private final LocalTime remainTime;
    private final LocalDateTime createdAt;

    public VacancyListResponse(Vacancy vacancy, StoreRemain storeRemain, Store store) {
        this.vacancyId = vacancy.getId();
        this.remainId = vacancy.getRemainId();
        this.storeName = store.getStoreName();
        this.remainDate = storeRemain.getRemainDate();
        this.remainTime = storeRemain.getRemainTime();
        this.createdAt = vacancy.getCreatedAt();
    }
}
