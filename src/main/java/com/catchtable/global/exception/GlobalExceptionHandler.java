package com.catchtable.global.exception;

import com.catchtable.global.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 - 잘못된 요청
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.success(400, e.getMessage()));
    }

    // 400 - 비즈니스 상태/규칙 위반
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.success(400, e.getMessage()));
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
                .body(ApiResponse.success(400, message));
    }

    // 403 - 권한 없음
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.success(403, e.getMessage()));
    }

    // 404 - 리소스 없음
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.success(404, e.getMessage()));
    }

    // 409 - 데이터 충돌
    @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(jakarta.persistence.OptimisticLockException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.success(409, "이미 마감된 요청입니다."));
    }

    // 500 - 서버 내부 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.success(500, "서버 내부 오류가 발생했습니다."));
    }
}
