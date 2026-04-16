package com.catchtable.reservation.scheduler;

import com.catchtable.notification.service.EmailService;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void sendReminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime from = now.toLocalTime().plusMinutes(20);
        LocalTime to = now.toLocalTime().plusMinutes(30);

        List<Reservation> targets = reservationRepository.findReminderTargets(
                ReservationStatus.CONFIRMED, today, from, to);

        for (Reservation reservation : targets) {
            String storeName = reservation.getStoreRemain().getStore().getStoreName();
            String remainDate = reservation.getStoreRemain().getRemainDate().toString();
            String remainTime = reservation.getStoreRemain().getRemainTime().toString();
            String email = reservation.getUser().getEmail();

            emailService.send(email,
                    "[캐치테이블] 예약 리마인드",
                    String.format("%s님, %s %s %s 예약 30분 전입니다.",
                            reservation.getUser().getNickname(),
                            storeName, remainDate, remainTime));

            reservation.markReminded();
        }

        if (!targets.isEmpty()) {
            log.info("[리마인드] 총 {}건 이메일 발송 완료", targets.size());
        }
    }
}
