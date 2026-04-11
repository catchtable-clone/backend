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

    // 기존 코드
    public static <T> ApiResponse<T> success(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }

    // 기존 코드
    public static ApiResponse<Void> success(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }

    // 성공 (데이터 포함)
    public static <T> ApiResponse<T> success(SuccessCode code, T data) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), data);
    }

    // 성공 (데이터 없음)
    public static ApiResponse<Void> success(SuccessCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), null);
    }

    // 실패 (데이터 포함)
    public static <T> ApiResponse<T> error(ErrorCode code, T data) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), data);
    }
    
    // 실패 (데이터 없음)
    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getMessage(), null);
    }
}