package com.catchtable.global.exception;

import com.catchtable.global.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// basePackages 제한: 액추에이터(/actuator/prometheus 등 org.springframework.boot.actuate.*)
// 엔드포인트의 예외까지 이 advice가 가로채면, ApiResponse(JSON)를 openmetrics 응답으로
// 직렬화하려다 HttpMessageNotWritableException → 스크랩 500 → 모니터링 DOWN 오표시가 발생.
// 본인 컨트롤러(com.catchtable.*)로만 적용 범위를 한정해 액추에이터를 건드리지 않게 한다.
@RestControllerAdvice(basePackages = "com.catchtable")
public class GlobalExceptionHandler {

    // CustomException 통합 처리 (403, 404, 400 등 ErrorCode 기반)
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getHttpStatus());
        // 일시적 장애(503)는 Retry-After 헤더로 클라이언트 자동 재시도 안내.
        // 회로 wait-duration-in-open-state(10s) 와 동일 값을 사용.
        if (errorCode == ErrorCode.COUPON_ISSUE_TEMPORARILY_UNAVAILABLE) {
            builder.header(HttpHeaders.RETRY_AFTER, "10");
        }
        return builder.body(ApiResponse.error(errorCode));
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
