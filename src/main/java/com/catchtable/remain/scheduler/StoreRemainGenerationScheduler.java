package com.catchtable.remain.scheduler;

import com.catchtable.remain.service.StoreRemainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 매일 새벽 4시에 "오늘 + 30일" 날짜의 예약 슬롯을 자동 생성한다.
 * 항상 오늘 기준 30일치 미래 슬롯이 DB에 존재하도록 슬라이딩 윈도우 방식으로 보충.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreRemainGenerationScheduler {

    private static final int FUTURE_DAYS = 30;

    private final StoreRemainService storeRemainService;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *")
    public void generateFutureSlots() {
        LocalDate targetDate = LocalDate.now(clock).plusDays(FUTURE_DAYS);
        int createdStoreCount = storeRemainService.generateDailySlotsForAllStores(targetDate);

        if (createdStoreCount > 0) {
            log.info("[슬롯 자동 생성] 대상 날짜: {}, {}개 매장에 슬롯 생성", targetDate, createdStoreCount);
        }
    }
}
