package com.catchtable.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(ResponseCode code, T data) {
        return new ApiResponse<>(code.getStatus(), code.getMessage(), data);
    }

    public static ApiResponse<Void> error(ResponseCode code) {
        return new ApiResponse<>(code.getStatus(), code.getMessage(), null);
    }

    public static ApiResponse<Void> error(ResponseCode code, String message) {
        return new ApiResponse<>(code.getStatus(), message, null);
    }
}
