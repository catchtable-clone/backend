package com.catchtable.remain.dto.projection;

import java.time.LocalTime;

/**
 * 매장별 슬롯 시간 조회용 Interface Projection.
 * (storeId, remainTime) 페어를 타입 안전하게 반환한다.
 */
public interface StoreRemainTimeView {

    Long getStoreId();

    LocalTime getRemainTime();
}
