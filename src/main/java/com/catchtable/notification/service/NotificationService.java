package com.catchtable.notification.service;

import com.catchtable.notification.entity.Notification;
import com.catchtable.notification.entity.NotificationType;
import com.catchtable.notification.repository.NotificationRepository;
import com.catchtable.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
}
