package com.catchtable.notification.service;

import com.catchtable.notification.event.ReservationCanceledEvent;
import com.catchtable.notification.event.ReservationChangedEvent;
import com.catchtable.notification.event.ReservationConfirmedEvent;
import com.catchtable.notification.event.ReservationReminderEvent;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.event.VacancyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ApplicationEvent를 받아 Kafka로 변환 발행.
 * @TransactionalEventListener(AFTER_COMMIT) → DB 트랜잭션이 커밋된 후에만 Kafka 메시지 전송.
 * 트랜잭션 롤백 시 Kafka 메시지도 발행 안 되어 ghost 알림(데이터 정합성 깨짐) 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationConfirmed(ReservationConfirmedEvent event) {
        kafkaTemplate.send("notification.reservation.confirmed", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationCanceled(ReservationCanceledEvent event) {
        kafkaTemplate.send("notification.reservation.canceled", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationChanged(ReservationChangedEvent event) {
        kafkaTemplate.send("notification.reservation.changed", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationVisited(ReservationVisitedEvent event) {
        kafkaTemplate.send("notification.reservation.visited", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishVacancyOpened(VacancyEvent event) {
        kafkaTemplate.send("notification.vacancy.opened", event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReservationReminder(ReservationReminderEvent event) {
        kafkaTemplate.send("notification.reservation.reminder", event);
    }
}
