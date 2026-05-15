package com.catchtable.remain.service;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 슬롯을 매장 단위 독립 트랜잭션(REQUIRES_NEW)으로 저장한다.
 * 한 매장 실패 시 해당 매장만 롤백되고 나머지 매장에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreRemainSlotWriter {

    private final StoreRemainRepository storeRemainRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSlots(List<StoreRemain> missingSlots) {
        storeRemainRepository.saveAll(missingSlots);
    }
}
