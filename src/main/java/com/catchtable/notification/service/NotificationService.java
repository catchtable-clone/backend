package com.catchtable.notification.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.dto.read.NotificationListResponse;
import com.catchtable.notification.entity.Notification;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.repository.NotificationRepository;
import com.catchtable.reservation.repository.ReservationRepository;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StoreRepository storeRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void createNotification(User user, NotificationType type, String title, String content, Long relatedItemId) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(content)
                .relatedItemId(relatedItemId)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationListResponse> getMyNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndIsDeletedFalse(userId, pageable)
                .map(notification -> new NotificationListResponse(notification, resolveStoreName(notification)));
    }

    // VACANCY 알림은 relatedItemId가 storeId, RESERVATION_* 알림은 reservationId
    private String resolveStoreName(Notification notification) {
        Long relatedId = notification.getRelatedItemId();
        if (relatedId == null) return null;

        return switch (notification.getType()) {
            case VACANCY -> storeRepository.findById(relatedId)
                    .map(s -> s.getStoreName())
                    .orElse(null);
            case RESERVATION_CONFIRMED, RESERVATION_CANCELED, RESERVATION_CHANGED, RESERVATION_VISITED, RESERVATION_REMINDER ->
                    reservationRepository.findByIdWithUserAndStoreRemainAndStore(relatedId)
                            .map(r -> r.getStoreRemain().getStore().getStoreName())
                            .orElse(null);
        };
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndIsDeletedFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_NOTIFICATION_OWNER);
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_NOTIFICATION_OWNER);
        }

        notification.markAsDeleted();
    }
}
