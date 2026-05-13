package com.catchtable.reservation.scheduler;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import com.catchtable.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 미완료(PENDING) 예약을 일정 시간 후 자동 정리한다.
 * - 좌석 race를 막기 위해 예약 생성 시 좌석을 선점하므로,
 *   결제 안 한 사용자가 영구히 좌석을 묶지 않도록 timeout 기반 cleanup이 필요하다.
 * - 트랜잭션은 ReservationService.expirePending에서 처리해 self-invocation AOP 문제를 회피.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupScheduler {

    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(5);
    private static final long FIXED_DELAY_MS = 60_000L;   // 1분

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void cleanupExpiredPending() {
        LocalDateTime threshold = LocalDateTime.now().minus(PENDING_TIMEOUT);
        List<Reservation> expired = reservationRepository
                .findExpiredPending(ReservationStatus.PENDING, threshold);
        if (expired.isEmpty()) {
            return;
        }
        log.info("PENDING 예약 cleanup 시작: {}건", expired.size());
        for (Reservation r : expired) {
            try {
                reservationService.expirePending(r.getId());
            } catch (Exception e) {
                log.warn("PENDING cleanup 실패: reservationId={}, reason={}", r.getId(), e.getMessage());
            }
        }
    }
}
