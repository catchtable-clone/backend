package com.catchtable.notification.entity;

import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Long relatedItemId; // 예약 상세나 상점 페이지 이동을 위한 ID (reservationId, storeId 등)

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Notification(User user, NotificationType type, String title, String content, Long relatedItemId) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.relatedItemId = relatedItemId;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void markAsDeleted() {
        this.isDeleted = true;
    }
}
