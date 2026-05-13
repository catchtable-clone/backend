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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreRemainService {

    private final StoreRemainRepository storeRemainRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public void generateMonthlyRemain(StoreRemainCreateRequestDto request) {

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime openTime;
        LocalTime closeTime;

        try {
            openTime = LocalTime.parse(store.getOpenTime(), formatter);
            closeTime = LocalTime.parse(store.getCloseTime(), formatter);
        } catch (Exception e) {
            log.error("매장 영업 시간 파싱 실패. storeId: {}, openTime: {}, closeTime: {}",
                    store.getId(), store.getOpenTime(), store.getCloseTime());
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        YearMonth targetMonth = YearMonth.of(request.year(), request.month());
        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth.atEndOfMonth();

        List<StoreRemain> remainsToSave = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalTime currentTime = openTime;
            while (currentTime.isBefore(closeTime)) {
                if (currentTime.plusHours(1).isAfter(closeTime)) {
                    break;
                }
                StoreRemain remain = StoreRemain.builder()
                        .store(store)
                        .remainDate(date)
                        .remainTime(currentTime)
                        .remainTeam(store.getTeam())
                        .build();
                remainsToSave.add(remain);
                currentTime = currentTime.plusHours(1);
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
     * 이미 그 날짜 슬롯이 하나라도 있는 매장은 스킵.
     *
     * @return 슬롯이 생성된 매장 수
     */
    @Transactional
    public int generateDailySlotsForAllStores(LocalDate targetDate) {
        List<Store> stores = storeRepository.findAllByIsDeletedFalse();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        int createdStoreCount = 0;
        for (Store store : stores) {
            if (storeRemainRepository.existsByStoreIdAndRemainDate(store.getId(), targetDate)) {
                continue;
            }

            try {
                LocalTime openTime = LocalTime.parse(store.getOpenTime(), formatter);
                LocalTime closeTime = LocalTime.parse(store.getCloseTime(), formatter);

                List<StoreRemain> slots = new ArrayList<>();
                LocalTime currentTime = openTime;
                while (currentTime.isBefore(closeTime)) {
                    if (currentTime.plusHours(1).isAfter(closeTime)) {
                        break;
                    }
                    slots.add(StoreRemain.builder()
                            .store(store)
                            .remainDate(targetDate)
                            .remainTime(currentTime)
                            .remainTeam(store.getTeam())
                            .build());
                    currentTime = currentTime.plusHours(1);
                }

                if (!slots.isEmpty()) {
                    storeRemainRepository.saveAll(slots);
                    createdStoreCount++;
                }
            } catch (Exception e) {
                log.error("[슬롯 자동 생성 실패] storeId={}, targetDate={}, error={}",
                        store.getId(), targetDate, e.getMessage());
            }
        }
        return createdStoreCount;
    }
}