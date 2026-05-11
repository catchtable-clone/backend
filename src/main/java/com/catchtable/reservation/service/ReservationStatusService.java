package com.catchtable.reservation.service;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 예약 상태 자동 전이 비즈니스 로직.
 * 스케줄러는 트리거만 담당하고, 실제 로직은 이 서비스가 가진다.
 * 테스트 시 Clock을 가짜로 주입하여 시간 의존성을 통제 가능.
 */
@Service
@RequiredArgsConstructor
public class ReservationStatusService {

    private static final int NOSHOW_THRESHOLD_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final Clock clock;

    /**
     * 예약 시각이 30분 이상 지났는데 CONFIRMED 상태로 남아있는 예약을 NOSHOW로 전환한다.
     * status = CONFIRMED 재확인으로 사용자가 동시에 VISITED 호출하는 경우 race 방지.
     *
     * @return 전환된 건수
     */
    @Transactional
    public int transitionExpiredConfirmedToNoshow() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMinutes(NOSHOW_THRESHOLD_MINUTES);
        LocalDate date = threshold.toLocalDate();
        LocalTime time = threshold.toLocalTime();

        List<Reservation> targets = reservationRepository.findNoshowTargets(
                ReservationStatus.CONFIRMED, date, time);

        int transitioned = 0;
        for (Reservation reservation : targets) {
            if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                continue;
            }

            reservation.changeStatus(ReservationStatus.NOSHOW);
            transitioned++;
        }
        return transitioned;
    }
}
