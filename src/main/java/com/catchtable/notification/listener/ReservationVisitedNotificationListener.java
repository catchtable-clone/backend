package com.catchtable.notification.listener;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.service.NotificationService;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationVisitedNotificationListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReservationVisitedEvent(ReservationVisitedEvent event) {
        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String title = "방문이 완료되었습니다.";
        String content = String.format("'%s' 매장 %s %s 방문이 완료되었습니다. 소중한 리뷰를 남겨주세요!",
                event.getStoreName(),
                event.getRemainDate(),
                event.getRemainTime());

        notificationService.createNotification(
                user,
                NotificationType.RESERVATION_VISITED,
                title,
                content,
                event.getReservationId()
        );

        log.info("[방문 완료 알림] userId: {}, reservationId: {} 알림 생성 완료", event.getUserId(), event.getReservationId());
    }
}
