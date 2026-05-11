package com.catchtable.reservation.scheduler;

import com.catchtable.notification.event.ReservationReminderEvent;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 1분 주기로 예약 시각 1시간 전 도달한 예약을 찾아 in-app 리마인드 알림을 발송한다.
     * 윈도우: now + 59분 ~ now + 60분 (1분 폭). reminded 플래그로 중복 발송 방지.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sendReminder() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime from = now.toLocalTime().plusMinutes(59);
        LocalTime to = now.toLocalTime().plusMinutes(60);

        List<Reservation> targets = reservationRepository.findReminderTargets(
                ReservationStatus.CONFIRMED, today, from, to);

        for (Reservation reservation : targets) {
            StoreRemain storeRemain = reservation.getStoreRemain();
            eventPublisher.publishEvent(new ReservationReminderEvent(
                    reservation.getId(),
                    reservation.getUser().getId(),
                    storeRemain.getStore().getStoreName(),
                    storeRemain.getRemainDate().toString(),
                    storeRemain.getRemainTime().toString()
            ));
            reservation.markReminded();
        }

        if (!targets.isEmpty()) {
            log.info("[리마인드] 총 {}건 in-app 알림 이벤트 발행", targets.size());
        }
    }
}
