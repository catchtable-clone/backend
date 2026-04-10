package com.catchtable.global.exception;

import com.catchtable.global.common.ResponseCode;
import lombok.Getter;

@Getter
public class AccessDeniedException extends RuntimeException {

    private final ResponseCode responseCode;

    public AccessDeniedException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
    }
}
