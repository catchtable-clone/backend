package com.catchtable.global.common;

import org.springframework.http.HttpStatus;

public interface ResponseCode {
    HttpStatus getHttpStatus();
    String getMessage();

    default String getCode() {
        return (this instanceof Enum<?> e) ? e.name() : getClass().getSimpleName();
    }
}
