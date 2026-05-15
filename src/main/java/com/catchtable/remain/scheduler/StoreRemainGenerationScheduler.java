package com.catchtable.remain.scheduler;

import com.catchtable.remain.service.StoreRemainService;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 매일 새벽 4시에 "오늘 포함 30일" 범위의 예약 슬롯을 자동 생성한다.
 * 항상 오늘부터 30일치 슬롯이 DB에 존재하도록 보충 (차집합 기반이라 이미 있는 날짜는 스킵).
 * 매장 목록은 범위 루프 시작 전 단 한 번만 조회해 30회 반복 조회 비용을 제거한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreRemainGenerationScheduler {

    private static final int RANGE_DAYS = 30;

    private final StoreRemainService storeRemainService;
    private final StoreRepository storeRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *")
    public void generateFutureSlots() {
        List<Store> stores = storeRepository.findAllByIsDeletedFalse();
        LocalDate startDate = LocalDate.now(clock);
        LocalDate endDate = startDate.plusDays(RANGE_DAYS - 1);

        int totalFilledStores = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            totalFilledStores += storeRemainService.generateDailySlotsForStores(stores, date);
        }

        if (totalFilledStores > 0) {
            log.info("[슬롯 자동 생성] 범위: {} ~ {}, 보충 발생 매장-일 합계: {}",
                    startDate, endDate, totalFilledStores);
        }
    }
}
