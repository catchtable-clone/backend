package com.catchtable.global.common;

import com.catchtable.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(SuccessCode code, T data) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getCode(), code.getMessage(), data);
    }

    public static ApiResponse<Void> success(SuccessCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getCode(), code.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getCode(), code.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.getHttpStatus().value(), code.getCode(), message, null);
    }
}
