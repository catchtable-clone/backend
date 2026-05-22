package com.catchtable.notification.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.ReservationCanceledEvent;
import com.catchtable.notification.event.ReservationChangedEvent;
import com.catchtable.notification.event.ReservationConfirmedEvent;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @KafkaListener(topics = "notification.reservation.confirmed", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationConfirmed(@Payload ReservationConfirmedEvent event) {
        log.info("[Kafka Consumer] 예약 확정 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUser(event.getUserId());

        String title = "예약이 확정되었습니다.";
        String content = String.format("'%s' 매장 %s %s 예약이 확정되었습니다.",
                event.getStoreName(),
                event.getRemainDate(),
                event.getRemainTime());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_CONFIRMED,
                title,
                content,
                event.getReservationId()
        );
    }

    @KafkaListener(topics = "notification.reservation.canceled", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationCanceled(@Payload ReservationCanceledEvent event) {
        log.info("[Kafka Consumer] 예약 취소 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUser(event.getUserId());

        String title = "예약이 취소되었습니다.";
        String content = String.format("'%s' 매장 %s %s 예약이 취소되었습니다.",
                event.getStoreName(),
                event.getRemainDate(),
                event.getRemainTime());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_CANCELED,
                title,
                content,
                event.getReservationId()
        );
    }

    @KafkaListener(topics = "notification.reservation.changed", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationChanged(@Payload ReservationChangedEvent event) {
        log.info("[Kafka Consumer] 예약 변경 이벤트 수신: newReservationId={}", event.getNewReservationId());
        User user = findUser(event.getUserId());

        String title = "예약이 변경되었습니다.";
        String content = String.format("'%s' 매장 예약이 %s %s에서 %s %s으로 변경되었습니다.",
                event.getStoreName(),
                event.getOldRemainDate(),
                event.getOldRemainTime(),
                event.getNewRemainDate(),
                event.getNewRemainTime());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_CHANGED,
                title,
                content,
                event.getNewReservationId()
        );
    }

    @KafkaListener(topics = "notification.reservation.visited", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationVisited(@Payload ReservationVisitedEvent event) {
        log.info("[Kafka Consumer] 방문 완료 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUser(event.getUserId());

        String title = "방문은 즐거우셨나요?";
        String content = String.format("'%s' 매장 방문이 완료되었습니다. 소중한 경험을 리뷰로 남겨주세요!",
                event.getStoreName());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_VISITED,
                title,
                content,
                event.getReservationId()
        );
    }

    @KafkaListener(topics = "notification.vacancy.opened", groupId = "catchtable-notification-group")
    @Transactional
    public void handleVacancyOpened(@Payload VacancyEvent event) {
        // 이 부분은 Redis 도입 후, Redis에서 구독자 목록을 가져와서 처리해야 합니다.
        // 현재는 구현하지 않고 로그만 남깁니다.
        log.info("[Kafka Consumer] 빈자리 발생 이벤트 수신: remainId={}", event.getRemainId());
        // TODO: Redis에서 remainId에 해당하는 구독자(userId) 목록 조회
        // TODO: 각 구독자에게 알림 생성 (notificationService.createNotification)
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[Kafka Consumer] 사용자 정보를 찾을 수 없습니다. userId={}", userId);
                    return new CustomException(ErrorCode.USER_NOT_FOUND);
                });
    }
}
