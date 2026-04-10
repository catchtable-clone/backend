package com.catchtable.global.exception;

import com.catchtable.global.common.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessCode implements ResponseCode {

    // Reservation 도메인
    RESERVATION_CREATE_SUCCESS(HttpStatus.CREATED, "예약이 성공적으로 생성되었습니다."),
    RESERVATION_LOOKUP_SUCCESS(HttpStatus.OK, "예약 조회가 완료되었습니다."),
    RESERVATION_CANCEL_SUCCESS(HttpStatus.OK, "예약이 성공적으로 취소되었습니다."),
    RESERVATION_UPDATE_SUCCESS(HttpStatus.OK, "예약이 성공적으로 변경되었습니다."),

    // Remain 도메인
    REMAIN_CREATE_SUCCESS(HttpStatus.CREATED, "예약 목록이 성공적으로 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
