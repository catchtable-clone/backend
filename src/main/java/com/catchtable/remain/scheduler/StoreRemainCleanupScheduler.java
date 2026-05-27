package com.catchtable.remain.scheduler;

import com.catchtable.remain.service.StoreRemainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 매일 새벽 4시 30분(슬롯 생성 직후)에 "지난 날짜의 미참조 슬롯"을 물리 삭제하여
 * store_remain을 30일 롤링 윈도우로 유지한다.
 *
 * - 생성 스케줄러(StoreRemainGenerationScheduler)가 오늘부터 30일치만 보충하므로,
 *   지나간 날짜 슬롯은 예약 가치가 없어 삭제 대상이다.
 * - 단, FK 체인(payment → reservation → store_remain, vacancy_subscriptions → store_remain)
 *   때문에 참조 슬롯을 지우면 FK 위반이 발생한다. 리포지토리 쿼리가 NOT EXISTS로 참조 슬롯을
 *   보존하므로 안전하다.
 * - 하루치(수백만 행)를 한 번에 DELETE하면 WAL 폭증으로 디스크가 다시 찰 수 있어,
 *   배치 단위 독립 트랜잭션으로 나눠 반복 삭제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreRemainCleanupScheduler {

    private static final int BATCH_SIZE = 10_000;
    private static final int MAX_ITERATIONS = 2_000;   // 안전 상한: 1회 실행당 최대 2천만 행

    private final StoreRemainService storeRemainService;
    private final Clock clock;

    @Scheduled(cron = "0 30 4 * * *")
    public void purgePastSlots() {
        LocalDate today = LocalDate.now(clock);
        int totalDeleted = 0;
        int iterations = 0;
        int deleted;
        do {
            deleted = storeRemainService.purgeUnreferencedPastSlotsBatch(today, BATCH_SIZE);
            totalDeleted += deleted;
            iterations++;
        } while (deleted == BATCH_SIZE && iterations < MAX_ITERATIONS);

        if (iterations >= MAX_ITERATIONS && deleted == BATCH_SIZE) {
            log.warn("[지난 슬롯 정리] 안전 상한({}배치) 도달 — 남은 슬롯은 다음 실행에서 처리됨", MAX_ITERATIONS);
        }
        if (totalDeleted > 0) {
            log.info("[지난 슬롯 정리] 미참조 과거 슬롯 삭제: {}건 ({}배치, 기준일 {})",
                    totalDeleted, iterations, today);
        }
    }
}
