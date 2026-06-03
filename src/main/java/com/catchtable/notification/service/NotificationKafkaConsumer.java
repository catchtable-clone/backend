package com.catchtable.notification.service;

import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.ReservationCanceledEvent;
import com.catchtable.notification.event.ReservationChangedEvent;
import com.catchtable.notification.event.ReservationConfirmedEvent;
import com.catchtable.notification.event.ReservationReminderEvent;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.vacancy.service.VacancyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(5);

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final StringRedisTemplate redisTemplate;
    private final VacancyService vacancyService;

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

    @KafkaListener(topics = "notification.reservation.canceled", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationCanceled(@Payload ReservationCanceledEvent event) {
        log.info("[Kafka Consumer] 예약 취소 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUserOrThrow(event.getUserId());

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

    @KafkaListener(topics = "notification.reservation.reminder", groupId = "catchtable-notification-group")
    @Transactional
    public void handleReservationReminder(@Payload ReservationReminderEvent event) {
        log.info("[Kafka Consumer] 예약 리마인더 이벤트 수신: reservationId={}", event.getReservationId());
        User user = findUserOrThrow(event.getUserId());

        String title = "예약 1시간 전입니다.";
        String content = String.format("'%s' 매장 %s %s 예약이 1시간 후에 시작됩니다.",
                event.getStoreName(),
                event.getRemainDate(),
                event.getRemainTime());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_REMINDER,
                title,
                content,
                event.getReservationId()
        );
    }

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

    @KafkaListener(topics = "notification.vacancy.opened", groupId = "catchtable-notification-group")
    @Transactional
    public void handleVacancyOpened(@Payload VacancyEvent event) {
        log.info("[Kafka Consumer] 빈자리 발생 이벤트 수신: remainId={}", event.getRemainId());

        // 1. 쿨다운 키 확인
        String cooldownKey = "cooldown:vacancy:" + event.getRemainId();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "locked", COOLDOWN_DURATION);

        if (Boolean.FALSE.equals(isNew)) {
            log.info("[Kafka Consumer] 쿨다운 적용 중, 빈자리 알림을 건너뜁니다. remainId={}", event.getRemainId());
            return;
        }

        StoreRemain storeRemain = storeRemainRepository.findById(event.getRemainId()).orElse(null);
        if (storeRemain == null) {
            log.warn("[Kafka Consumer] 빈자리 알림 처리 실패: 존재하지 않는 remainId={}", event.getRemainId());
            return;
        }

        String redisKey = vacancyService.generateRedisKey(storeRemain);
        Set<String> subscriberIds = redisTemplate.opsForSet().members(redisKey);

        if (subscriberIds == null || subscriberIds.isEmpty()) {
            log.info("[Kafka Consumer] 빈자리 알림 구독자가 없습니다. key={}", redisKey);
            return;
        }

        List<Long> userIds = subscriberIds.stream().map(Long::parseLong).collect(Collectors.toList());
        List<User> users = userRepository.findAllById(userIds);

        String title = "빈자리 알림";
        String content = String.format("'%s' 매장 %s %s에 빈자리가 발생했습니다! 지금 바로 예약하세요.",
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate(),
                storeRemain.getRemainTime());

        for (User user : users) {
            notificationService.createNotification(
                    user,
                    NotificationType.VACANCY,
                    title,
                    content,
                    storeRemain.getStore().getId()
            );
        }

        log.info("[Kafka Consumer] {}명에게 빈자리 알림을 생성했습니다. key={}", users.size(), redisKey);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[Kafka Consumer] 사용자 정보를 찾을 수 없습니다. userId={}", userId);
                    return new RuntimeException("사용자를 찾을 수 없음: " + userId);
                });
    }
}
