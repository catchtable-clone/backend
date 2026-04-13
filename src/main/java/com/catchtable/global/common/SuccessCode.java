package com.catchtable.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessCode implements ResponseCode {

    // Store
    STORE_CREATED(HttpStatus.CREATED, "매장이 등록되었습니다."),
    STORE_LIST_OK(HttpStatus.OK, "매장 목록을 조회했습니다."),
    STORE_DETAIL_OK(HttpStatus.OK, "매장 정보를 조회했습니다."),
    STORE_UPDATED(HttpStatus.OK, "매장 정보가 수정되었습니다."),
    STORE_STATUS_UPDATED(HttpStatus.OK, "매장 상태가 변경되었습니다."),

    // Coupon
    COUPON_TEMPLATE_CREATED(HttpStatus.CREATED, "쿠폰 템플릿이 생성되었습니다."),
    COUPON_ISSUED(HttpStatus.OK, "쿠폰이 발급되었습니다."),
    COUPON_LIST_OK(HttpStatus.OK, "내 쿠폰 목록을 조회했습니다."),

    // Reservation
    RESERVATION_CREATE_SUCCESS(HttpStatus.CREATED, "예약이 성공적으로 생성되었습니다."),
    RESERVATION_LOOKUP_SUCCESS(HttpStatus.OK, "예약 조회가 완료되었습니다."),
    RESERVATION_CANCEL_SUCCESS(HttpStatus.OK, "예약이 성공적으로 취소되었습니다."),
    RESERVATION_UPDATE_SUCCESS(HttpStatus.OK, "예약이 성공적으로 변경되었습니다."),

    // Remain
    REMAIN_CREATE_SUCCESS(HttpStatus.CREATED, "예약 목록이 성공적으로 생성되었습니다."),
    REMAIN_LOOKUP_SUCCESS(HttpStatus.OK, "예약 가능 시간 조회가 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
