package com.catchtable.global.exception;

import com.catchtable.global.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // CustomException 통합 처리 (403, 404, 400 등 ErrorCode 기반)
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    // 400 - 입력값 검증 실패 (@Valid 에러)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, message));
    }

    // 409 - 데이터 충돌 (낙관적 락 - JPA 표준)
    @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(jakarta.persistence.OptimisticLockException e) {
        return ResponseEntity
                .status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    // 409 - 데이터 충돌 (낙관적 락 - Spring 래핑)
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringOptimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ResponseEntity
                .status(ErrorCode.OPTIMISTIC_LOCK_CONFLICT.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    // 500 - 서버 내부 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
