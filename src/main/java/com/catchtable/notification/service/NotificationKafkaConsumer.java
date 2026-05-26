package com.catchtable.notification.service;

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
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.retrytopic.DltStrategy;

import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Kafka 알림 메시지를 수신하고 처리하는 컨슈머 클래스.
 * 모든 리스너는 'catchtable-notification-group' 그룹에 속합니다.
 *
 * @RetryableTopic: 메시지 처리 실패 시 재시도 및 DLQ(Dead Letter Queue) 처리를 자동으로 구성합니다.
 *  - attempts: 최대 시도 횟수 (기본값 3)
 *  - backoff: 재시도 간격 설정 (2초 간격)
 *  - dltStrategy: DLQ 처리 전략. ALWAYS_RETRY_ON_ERROR는 모든 예외에 대해 재시도 후 DLQ로 보냅니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @RetryableTopic(
            attempts = "3",
            dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR
    )
    @KafkaListener(topics = "notification.reservation.confirmed", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationConfirmed(@Payload ReservationConfirmedEvent event) {
        log.info("[Kafka Consumer] 예약 확정 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUserOrThrow(event.getUserId());

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

    @RetryableTopic(attempts = "3", dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = "notification.reservation.canceled", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationCanceled(@Payload ReservationCanceledEvent event) {
        log.info("[Kafka Consumer] 예약 취소 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUserOrThrow(event.getUserId());


        // --- DLQ 테스트용 강제 에러 발생 코드 시작 ---
//        if (true) {
//            log.warn("=== DLQ 테스트: 강제로 에러를 발생시킵니다! ===");
//            throw new RuntimeException("DLQ 테스트용 임시 예외");
//        }


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

    @RetryableTopic(attempts = "3", dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = "notification.reservation.changed", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationChanged(@Payload ReservationChangedEvent event) {
        log.info("[Kafka Consumer] 예약 변경 이벤트 수신: newReservationId={}", event.getNewReservationId());
        User user = findUserOrThrow(event.getUserId());

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

    @RetryableTopic(attempts = "3", dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = "notification.reservation.visited", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationVisited(@Payload ReservationVisitedEvent event) {
        log.info("[Kafka Consumer] 방문 완료 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUserOrThrow(event.getUserId());

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

    @RetryableTopic(attempts = "3", dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR)
    @KafkaListener(topics = "notification.vacancy.opened", groupId = "catchtable-notification-group")
    @Transactional
    public void handleVacancyOpened(@Payload VacancyEvent event) {
        // 이 부분은 Redis 도입 후, Redis에서 구독자 목록을 가져와서 처리해야 합니다.
        // 현재는 구현하지 않고 로그만 남깁니다.
        log.info("[Kafka Consumer] 빈자리 발생 이벤트 수신: remainId={}", event.getRemainId());
        // TODO: Redis에서 remainId에 해당하는 구독자(userId) 목록 조회
        // TODO: 각 구독자에게 알림 생성 (notificationService.createNotification)
    }

    @DltHandler
    public void handleDlt(Object message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("[DLT] 메시지 처리 최종 실패. Topic: {}, Message: {}", topic, message.toString());
        // TODO: 운영자에게 슬랙(Slack) 알림 발송 등의 후속 조치 구현
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    // 존재하지 않는 사용자는 재시도해도 성공할 수 없으므로,
                    // 무한 재시도를 유발하지 않도록 Non-Retryable 예외를 던지는 것이 더 좋지만,
                    // 여기서는 일관성을 위해 일단 모든 예외를 재시도하도록 둡니다.
                    log.error("[Kafka Consumer] 사용자 정보를 찾을 수 없습니다. userId={}", userId);
                    return new RuntimeException("사용자를 찾을 수 없음: " + userId);
                });
    }
}
