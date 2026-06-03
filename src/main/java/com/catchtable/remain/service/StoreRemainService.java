package com.catchtable.remain.service;

import com.catchtable.remain.dto.read.StoreRemainResponseDto;
import com.catchtable.remain.dto.create.StoreRemainCreateRequestDto;
import com.catchtable.remain.dto.projection.StoreRemainTimeView;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreRemainService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int STORE_CHUNK_SIZE = 500;

    private final StoreRemainRepository storeRemainRepository;
    private final StoreRepository storeRepository;
    private final StoreRemainSlotWriter storeRemainSlotWriter;

    @Transactional
    public void generateMonthlyRemain(StoreRemainCreateRequestDto request) {

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        LocalTime openTime;
        LocalTime closeTime;

        try {
            openTime = LocalTime.parse(store.getOpenTime(), TIME_FORMATTER);
            closeTime = LocalTime.parse(store.getCloseTime(), TIME_FORMATTER);
        } catch (Exception e) {
            log.error("매장 영업 시간 파싱 실패. storeId: {}, openTime: {}, closeTime: {}",
                    store.getId(), store.getOpenTime(), store.getCloseTime());
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        YearMonth targetMonth = YearMonth.of(request.year(), request.month());
        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth.atEndOfMonth();

        List<LocalTime> slotTimes = buildSlotTimes(openTime, closeTime);
        List<StoreRemain> remainsToSave = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (LocalTime time : slotTimes) {
                remainsToSave.add(StoreRemain.builder()
                        .store(store)
                        .remainDate(date)
                        .remainTime(time)
                        .remainTeam(store.getTeam())
                        .build());
            }
        }

        storeRemainRepository.saveAll(remainsToSave);
    }

    @Tool(description = "특정 매장의 특정 날짜에 예약 가능한 시간대를 조회합니다. '로코페페 5월 20일 예약 가능한 시간', '○○ 매장 언제 예약돼' 등의 요청에 사용하세요.")
    @Transactional(readOnly = true)
    public String getAvailableTimeSlotsForAi(
            @ToolParam(description = "매장 이름 (정확한 이름)") String storeName,
            @ToolParam(description = "조회할 날짜, ISO 형식 (예: 2025-05-20)") LocalDate date
    ) {
        Store store = storeRepository.findByStoreNameIgnoreCaseAndIsDeletedFalse(storeName).orElse(null);

        if (store == null) {
            return "'" + storeName + "' 매장을 찾을 수 없습니다. 매장 이름을 다시 확인해주세요.";
        }

        List<StoreRemain> remains = storeRemainRepository.findAllByStoreIdAndDate(store.getId(), date)
                .stream()
                .filter(r -> r.getRemainTeam() > 0)
                .toList();

        if (remains.isEmpty()) {
            return date + "에 " + storeName + " 예약 가능한 시간대가 없습니다.";
        }

        String times = remains.stream()
                .map(r -> r.getRemainTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                .collect(java.util.stream.Collectors.joining(", "));

        return date + " " + storeName + " 예약 가능한 시간: " + times;
    }

    @Transactional(readOnly = true)
    public List<StoreRemainResponseDto> getStoreRemains(Long storeId, LocalDate date) {

        List<StoreRemain> remains = storeRemainRepository.findAllByStoreIdAndDate(storeId, date);

        return remains.stream()
                .map(remain -> new StoreRemainResponseDto(
                        remain.getId(),
                        remain.getRemainDate(),
                        remain.getRemainTime(),
                        remain.getRemainTeam()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StoreRemain> findAvailableRemain(String storeName, LocalDate date, LocalTime time) {
        return storeRemainRepository.findByStoreNameAndDateTime(storeName, date, time)
                .filter(remain -> remain.getRemainTeam() > 0);
    }

    /**
     * 모든 활성 매장에 대해 지정된 날짜의 슬롯을 생성한다.
     * 단일 호출 편의용 - 매장 목록 조회 + 영업시간 사전 계산 후 generateDailySlotsForPlans에 위임한다.
     * 30일 같은 범위 루프에서는 buildSlotPlans를 1회 호출 후 generateDailySlotsForPlans를 직접 사용하라.
     *
     * @return 슬롯이 보충된 매장 수
     */
    @Transactional
    public int generateDailySlotsForAllStores(LocalDate targetDate) {
        List<Store> stores = storeRepository.findAllByIsDeletedFalse();
        return generateDailySlotsForPlans(buildSlotPlans(stores), targetDate);
    }

    /**
     * 지난 날짜의 미참조 슬롯을 한 배치 물리 삭제한다 (배치당 독립 트랜잭션).
     * 스케줄러가 반환값이 batchSize 미만이 될 때까지 반복 호출하여 롤링 윈도우를 유지한다.
     * 참조 슬롯 보존·FK 안전성은 리포지토리 쿼리(deleteUnreferencedPastSlots)에서 보장한다.
     *
     * @return 이번 배치에서 삭제된 행 수
     */
    @Transactional
    public int purgeUnreferencedPastSlotsBatch(LocalDate today, int batchSize) {
        return storeRemainRepository.deleteUnreferencedPastSlots(today, batchSize);
    }

    /**
     * 매장 목록을 받아 매장별 슬롯 계획(영업시간 파싱 + 시간 슬롯 목록)을 1회 계산한다.
     * 스케줄러가 30일 루프 시작 전 1회 호출하면 동일 매장에 대한 반복 파싱을 제거할 수 있다.
     * 영업시간 파싱 실패 매장은 결과에서 제외한다.
     */
    public List<StoreSlotPlan> buildSlotPlans(List<Store> stores) {
        List<StoreSlotPlan> plans = new ArrayList<>(stores.size());
        for (Store store : stores) {
            String openTimeStr = store.getOpenTime();
            String closeTimeStr = store.getCloseTime();
            if (openTimeStr == null || closeTimeStr == null) {
                log.warn("[영업시간 누락] storeId={}, openTime={}, closeTime={}",
                        store.getId(), openTimeStr, closeTimeStr);
                continue;
            }
            try {
                LocalTime openTime = LocalTime.parse(openTimeStr, TIME_FORMATTER);
                LocalTime closeTime = LocalTime.parse(closeTimeStr, TIME_FORMATTER);
                List<LocalTime> expectedTimes = buildSlotTimes(openTime, closeTime);
                if (expectedTimes.isEmpty()) {
                    continue;
                }
                plans.add(new StoreSlotPlan(store, expectedTimes));
            } catch (Exception e) {
                log.error("[영업시간 파싱 실패] storeId={}, openTime={}, closeTime={}",
                        store.getId(), openTimeStr, closeTimeStr);
            }
        }
        return plans;
    }

    /**
     * 사전 계산된 슬롯 계획에 따라 지정된 날짜의 슬롯을 생성한다.
     * - 매장 N개씩 청크 단위로 기존 슬롯을 조회하고 저장하여 메모리 부담 최소화
     * - 청크 단위 REQUIRES_NEW 트랜잭션으로 batch_size 효과 + 실패 격리 동시 확보
     * - 영업시간 파싱/슬롯 계산은 buildSlotPlans에서 이미 완료되어 루프마다 반복하지 않는다.
     *
     * @return 슬롯이 보충된 매장 수
     */
    public int generateDailySlotsForPlans(List<StoreSlotPlan> plans, LocalDate targetDate) {
        int filledStoreCount = 0;
        for (int i = 0; i < plans.size(); i += STORE_CHUNK_SIZE) {
            int end = Math.min(i + STORE_CHUNK_SIZE, plans.size());
            List<StoreSlotPlan> chunk = plans.subList(i, end);

            List<Long> chunkStoreIds = chunk.stream().map(p -> p.store().getId()).toList();
            Map<Long, Set<LocalTime>> existingByStore = fetchExistingTimesByStores(chunkStoreIds, targetDate);

            List<StoreRemain> chunkMissingSlots = new ArrayList<>();
            int chunkFilledCount = 0;

            for (StoreSlotPlan plan : chunk) {
                List<StoreRemain> storeMissing = buildMissingSlotsForPlan(plan, targetDate, existingByStore);
                if (!storeMissing.isEmpty()) {
                    chunkMissingSlots.addAll(storeMissing);
                    chunkFilledCount++;
                }
            }

            if (chunkMissingSlots.isEmpty()) {
                continue;
            }

            try {
                storeRemainSlotWriter.saveSlots(chunkMissingSlots);
                filledStoreCount += chunkFilledCount;
                log.info("[슬롯 청크 저장] targetDate={}, 매장범위=[{}~{}], 매장수={}, 슬롯수={}",
                        targetDate, chunk.get(0).store().getId(), chunk.get(chunk.size() - 1).store().getId(),
                        chunkFilledCount, chunkMissingSlots.size());
            } catch (Exception e) {
                log.error("[슬롯 청크 저장 실패] targetDate={}, 매장범위=[{}~{}], error={}",
                        targetDate, chunk.get(0).store().getId(), chunk.get(chunk.size() - 1).store().getId(),
                        e.getMessage());
            }
        }
        return filledStoreCount;
    }

    /**
     * 사전 계산된 매장 슬롯 계획에 대해 부족한 슬롯 목록을 메모리에서 계산한다 (DB 접근 없음).
     */
    private List<StoreRemain> buildMissingSlotsForPlan(
            StoreSlotPlan plan, LocalDate targetDate, Map<Long, Set<LocalTime>> existingByStore) {
        Store store = plan.store();
        Set<LocalTime> existingTimes = existingByStore.getOrDefault(store.getId(), Set.of());

        List<StoreRemain> missingSlots = new ArrayList<>();
        for (LocalTime time : plan.expectedTimes()) {
            if (existingTimes.contains(time)) {
                continue;
            }
            missingSlots.add(StoreRemain.builder()
                    .store(store)
                    .remainDate(targetDate)
                    .remainTime(time)
                    .remainTeam(store.getTeam())
                    .build());
        }
        return missingSlots;
    }

    /**
     * 지정된 매장 ID 목록과 날짜에 해당하는 기존 슬롯 시간을 한 번에 조회하여 매장별 Set으로 묶는다.
     * 청크 범위로 한정하여 메모리 사용량을 매장 전체가 아닌 청크 크기에 비례하도록 제한한다.
     */
    private Map<Long, Set<LocalTime>> fetchExistingTimesByStores(List<Long> storeIds, LocalDate targetDate) {
        Map<Long, Set<LocalTime>> map = new HashMap<>();
        for (StoreRemainTimeView view : storeRemainRepository.findStoreIdAndTimesByDateAndStoreIds(targetDate, storeIds)) {
            map.computeIfAbsent(view.getStoreId(), k -> new HashSet<>()).add(view.getRemainTime());
        }
        return map;
    }

    /**
     * 영업시간 범위로 1시간 단위 슬롯 시간 목록을 생성한다.
     * 자정 가로지름(예: 22:00~02:00)도 처리하도록 분 단위로 변환해 계산한다.
     * 마지막 슬롯은 closeTime 을 넘지 않는다.
     */
    private List<LocalTime> buildSlotTimes(LocalTime openTime, LocalTime closeTime) {
        int openMinutes = openTime.getHour() * 60 + openTime.getMinute();
        int closeMinutes = closeTime.getHour() * 60 + closeTime.getMinute();
        // closeTime 이 openTime 보다 작거나 같으면 자정 가로지름 → 24h 가산
        if (closeMinutes <= openMinutes) {
            closeMinutes += 24 * 60;
        }

        List<LocalTime> times = new ArrayList<>();
        for (int m = openMinutes; m + 60 <= closeMinutes; m += 60) {
            times.add(LocalTime.of((m / 60) % 24, m % 60));
        }
        return times;
    }
}