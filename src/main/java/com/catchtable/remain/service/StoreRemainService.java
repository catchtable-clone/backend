package com.catchtable.remain.service;

import com.catchtable.remain.dto.read.StoreRemainResponseDto;
import com.catchtable.remain.dto.create.StoreRemainCreateRequestDto;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 단일 호출 편의용 - 내부적으로 매장 목록을 조회한 뒤 generateDailySlotsForStores에 위임한다.
     * 30일 같은 범위 루프에서는 매장 조회 중복을 피하기 위해 generateDailySlotsForStores를 직접 호출하라.
     *
     * @return 슬롯이 보충된 매장 수
     */
    @Transactional
    public int generateDailySlotsForAllStores(LocalDate targetDate) {
        List<Store> stores = storeRepository.findAllByIsDeletedFalse();
        return generateDailySlotsForStores(stores, targetDate);
    }

    /**
     * 주어진 매장 목록에 대해 지정된 날짜의 슬롯을 생성한다.
     * - 해당 날짜의 기존 슬롯을 단 1회 조회하여 메모리에서 매장별 차집합 계산 (N+1 쿼리 제거)
     * - 매장 N개씩 청크 단위 트랜잭션(REQUIRES_NEW)으로 저장
     *   -> 청크 안에서 batch_size 효과 발휘 + 청크 단위 실패 격리
     *
     * @return 슬롯이 보충된 매장 수
     */
    public int generateDailySlotsForStores(List<Store> stores, LocalDate targetDate) {
        Map<Long, Set<LocalTime>> existingByStore = fetchExistingTimesByStore(targetDate);

        int filledStoreCount = 0;
        for (int i = 0; i < stores.size(); i += STORE_CHUNK_SIZE) {
            int end = Math.min(i + STORE_CHUNK_SIZE, stores.size());
            List<Store> chunk = stores.subList(i, end);

            List<StoreRemain> chunkMissingSlots = new ArrayList<>();
            int chunkFilledCount = 0;

            for (Store store : chunk) {
                try {
                    List<StoreRemain> storeMissing = buildMissingSlotsForStore(store, targetDate, existingByStore);
                    if (!storeMissing.isEmpty()) {
                        chunkMissingSlots.addAll(storeMissing);
                        chunkFilledCount++;
                    }
                } catch (Exception e) {
                    log.error("[슬롯 계산 실패] storeId={}, targetDate={}, error={}",
                            store.getId(), targetDate, e.getMessage());
                }
            }

            if (chunkMissingSlots.isEmpty()) {
                continue;
            }

            try {
                storeRemainSlotWriter.saveSlots(chunkMissingSlots);
                filledStoreCount += chunkFilledCount;
                log.info("[슬롯 청크 저장] targetDate={}, 매장범위=[{}~{}], 매장수={}, 슬롯수={}",
                        targetDate, chunk.get(0).getId(), chunk.get(chunk.size() - 1).getId(),
                        chunkFilledCount, chunkMissingSlots.size());
            } catch (Exception e) {
                log.error("[슬롯 청크 저장 실패] targetDate={}, 매장범위=[{}~{}], error={}",
                        targetDate, chunk.get(0).getId(), chunk.get(chunk.size() - 1).getId(),
                        e.getMessage());
            }
        }
        return filledStoreCount;
    }

    /**
     * 매장 1개에 대해 부족한 슬롯 목록을 메모리에서 계산한다 (DB 접근 없음).
     */
    private List<StoreRemain> buildMissingSlotsForStore(
            Store store, LocalDate targetDate, Map<Long, Set<LocalTime>> existingByStore) {
        LocalTime openTime = LocalTime.parse(store.getOpenTime(), TIME_FORMATTER);
        LocalTime closeTime = LocalTime.parse(store.getCloseTime(), TIME_FORMATTER);

        List<LocalTime> expectedTimes = buildSlotTimes(openTime, closeTime);
        if (expectedTimes.isEmpty()) {
            return List.of();
        }

        Set<LocalTime> existingTimes = existingByStore.getOrDefault(store.getId(), Set.of());

        List<StoreRemain> missingSlots = new ArrayList<>();
        for (LocalTime time : expectedTimes) {
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
     * 지정된 날짜의 모든 매장 슬롯 시간을 단 1회 쿼리로 조회하여 매장별 Set으로 묶는다.
     */
    private Map<Long, Set<LocalTime>> fetchExistingTimesByStore(LocalDate targetDate) {
        Map<Long, Set<LocalTime>> map = new HashMap<>();
        for (Object[] row : storeRemainRepository.findStoreIdAndTimesByDate(targetDate)) {
            Long storeId = (Long) row[0];
            LocalTime time = (LocalTime) row[1];
            map.computeIfAbsent(storeId, k -> new HashSet<>()).add(time);
        }
        return map;
    }

    /**
     * 영업시간 범위로 1시간 단위 슬롯 시간 목록을 생성한다.
     * 마지막 슬롯은 closeTime을 넘지 않아야 한다.
     */
    private List<LocalTime> buildSlotTimes(LocalTime openTime, LocalTime closeTime) {
        List<LocalTime> times = new ArrayList<>();
        LocalTime current = openTime;
        while (current.isBefore(closeTime)) {
            if (current.plusHours(1).isAfter(closeTime)) {
                break;
            }
            times.add(current);
            current = current.plusHours(1);
        }
        return times;
    }
}