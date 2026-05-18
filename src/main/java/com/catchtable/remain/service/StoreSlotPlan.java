package com.catchtable.remain.service;

import com.catchtable.store.entity.Store;

import java.time.LocalTime;
import java.util.List;

/**
 * 매장 1개에 대해 미리 파싱/계산해 둔 슬롯 계획.
 * 영업시간 문자열 파싱과 슬롯 시간 목록 생성을 30일 루프마다 반복하지 않기 위해
 * 스케줄러 시작 시점에 1회 계산해 재사용한다.
 */
public record StoreSlotPlan(Store store, List<LocalTime> expectedTimes) {
}
