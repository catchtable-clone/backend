package com.catchtable.reservation.scheduler;

import com.catchtable.reservation.service.ReservationStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStatusScheduler {

    private final ReservationStatusService reservationStatusService;

    /**
     * 10분 주기로 NOSHOW 자동 전환 트리거.
     * 실제 비즈니스 로직은 ReservationStatusService가 담당.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void transitionToNoshow() {
        int transitioned = reservationStatusService.transitionExpiredConfirmedToNoshow();
        if (transitioned > 0) {
            log.info("[NOSHOW 자동 전환] {}건 처리 완료", transitioned);
        }
    }
}
