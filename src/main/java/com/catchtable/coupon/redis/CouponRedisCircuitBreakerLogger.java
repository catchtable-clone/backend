package com.catchtable.coupon.redis;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * coupon-redis 회로의 상태 전환을 운영 로그에 남긴다.
 * 회로가 OPEN 으로 갔다 → Redis 장애 신호.
 * HALF_OPEN → CLOSED 면 복구 완료.
 * 사후 추적·알람 룰 작성의 근거가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRedisCircuitBreakerLogger {

    private static final String INSTANCE = "coupon-redis";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    void registerEventListeners() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(INSTANCE);
        cb.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("[CircuitBreaker:{}] 상태 전환 {} → {}",
                                INSTANCE,
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onCallNotPermitted(event ->
                        log.debug("[CircuitBreaker:{}] OPEN 상태로 호출 차단됨", INSTANCE))
                .onError(event ->
                        log.debug("[CircuitBreaker:{}] 호출 실패: {}",
                                INSTANCE, event.getThrowable().toString()));
    }
}
