package com.catchtable.global.exception;

import com.catchtable.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// basePackages 제한: 액추에이터(/actuator/prometheus 등 org.springframework.boot.actuate.*)
// 엔드포인트의 예외까지 이 advice가 가로채면, ApiResponse(JSON)를 openmetrics 응답으로
// 직렬화하려다 HttpMessageNotWritableException → 스크랩 500 → 모니터링 DOWN 오표시가 발생.
// 본인 컨트롤러(com.catchtable.*)로만 적용 범위를 한정해 액추에이터를 건드리지 않게 한다.
@Slf4j
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

    // 400 - 쿼리/path 파라미터 타입 불일치 (enum 변환 실패 등)
    // 예: ?category=CAFE 에서 CAFE 가 Category enum 에 없으면 ConversionFailedException 발생.
    // 핸들러 없을 시 Exception.class 로 잡혀 500 으로 응답되던 문제 해결.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = String.format("파라미터 '%s' 의 값 '%s' 이(가) %s 타입으로 변환할 수 없습니다.",
                e.getName(),
                e.getValue(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "?");
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, message));
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

    // 500 - 서버 내부 오류 (Unhandled)
    // log.error 로 클래스/메시지/스택트레이스를 남겨야 원인 추적 가능.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception → INTERNAL_ERROR: {}", e.getMessage(), e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
