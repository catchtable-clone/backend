package com.catchtable.notification.listener;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.event.ReservationChangedEvent;
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
public class ReservationChangedNotificationListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReservationChangedEvent(ReservationChangedEvent event) {
        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String title = "예약이 변경되었습니다.";
        String content = String.format("'%s' 매장 예약이 %s %s에서 %s %s로 변경되었습니다.",
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

        log.info("[예약 변경 알림] userId: {}, newReservationId: {} 알림 생성 완료",
                event.getUserId(), event.getNewReservationId());
    }
}
