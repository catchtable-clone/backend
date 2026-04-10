package com.catchtable.remain.service;

import com.catchtable.remain.dto.create.StoreRemainCreateRequestDto;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreRemainService {

    private final StoreRemainRepository storeRemainRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public void generateMonthlyRemain(StoreRemainCreateRequestDto request) {

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 매장입니다."));

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
            throw new IllegalArgumentException("매장의 영업 시간 형식이 올바르지 않습니다.");
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
}
