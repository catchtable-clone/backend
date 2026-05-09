package com.catchtable.reservation.entity;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELED,
    PAYMENT_FAILED, // 결제 실패/취소로 무산된 예약 (사용자 예약 취소와 구분)
    NOSHOW,
    VISITED,
    REPLACED        // 예약 변경으로 대체된 기존 예약 (취소/노쇼와 별도로 관리)
}
