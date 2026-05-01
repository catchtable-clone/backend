package com.catchtable.reservation.entity;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELED,
    NOSHOW,
    VISITED,
    REPLACED   // 예약 변경으로 대체된 기존 예약 (취소/노쇼와 별도로 관리)
}
