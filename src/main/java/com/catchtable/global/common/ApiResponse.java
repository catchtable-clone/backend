package com.catchtable.global.common;

import com.catchtable.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    // 성공 (데이터 포함)
    public static <T> ApiResponse<T> success(SuccessCode code, T data) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), data);
    }

    // 성공 (데이터 없음)
    public static ApiResponse<Void> success(SuccessCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), null);
    }

    // 실패 (데이터 없음)
    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), null);
    }

    // 실패 (커스텀 메시지)
    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.getHttpStatus().value(), message, null);
    }
}
