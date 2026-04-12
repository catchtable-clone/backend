package com.catchtable.global.exception;

import com.catchtable.global.common.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode implements ResponseCode {

    // Reservation 도메인
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예약입니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "본인의 예약만 접근할 수 있습니다."),
    ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "이미 취소된 예약입니다."),

    // StoreRemain 도메인
    REMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예약 시간대입니다."),
    REMAIN_EXHAUSTED(HttpStatus.BAD_REQUEST, "해당 시간대의 예약이 마감되었습니다."),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "이미 다른 사용자가 예약하여 마감되었습니다. 다시 시도해주세요."),

    // User 도메인
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
