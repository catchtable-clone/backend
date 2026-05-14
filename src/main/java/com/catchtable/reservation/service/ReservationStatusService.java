package com.catchtable.reservation.service;

import com.catchtable.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

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
     * 예약 시각이 30분 이상 지났는데 CONFIRMED 상태로 남아있는 예약을 NOSHOW로 일괄 전환한다.
     * 벌크 UPDATE라 DB 왕복 1회로 처리. WHERE status=CONFIRMED 조건이 동시성 가드 역할.
     *
     * @return 전환된 건수
     */
    @Transactional
    public int transitionExpiredConfirmedToNoshow() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minusMinutes(NOSHOW_THRESHOLD_MINUTES);

        return reservationRepository.bulkTransitionToNoshow(
                threshold.toLocalDate(),
                threshold.toLocalTime(),
                now);
    }
}
