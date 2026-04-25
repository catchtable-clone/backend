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
import java.util.stream.Collectors;

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

        // 매장의 문자열 형태("10:00") 영업 시간을 LocalTime으로 파싱
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

        // 해당 월의 1일과 말일 계산
        YearMonth targetMonth = YearMonth.of(request.year(), request.month());
        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth.atEndOfMonth();

        List<StoreRemain> remainsToSave = new ArrayList<>();

        // 1일부터 말일까지 생성
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            
            LocalTime currentTime = openTime;
            
            // 시간별 루프 (오픈 시간부터 마감 시간 1시간 전까지)
            while (currentTime.isBefore(closeTime)) {

                if (currentTime.plusHours(1).isAfter(closeTime)) {
                    break;
                }

                // 엔티티 생성 (잔여 팀 수는 매장의 team)
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

        // 저장
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
                .collect(Collectors.toList());
    }
}
