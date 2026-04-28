package com.catchtable.notification.dto.read;

import com.catchtable.notification.entity.Notification;
import com.catchtable.notification.entity.NotificationType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationListResponse {

    private final Long notificationId;
    private final NotificationType type;
    private final String title;
    private final String content;
    private final Long relatedItemId;
    private final boolean isRead;
    private final LocalDateTime createdAt;

    public NotificationListResponse(Notification notification) {
        this.notificationId = notification.getId();
        this.type = notification.getType();
        this.title = notification.getTitle();
        this.content = notification.getContent();
        this.relatedItemId = notification.getRelatedItemId();
        this.isRead = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }
}
