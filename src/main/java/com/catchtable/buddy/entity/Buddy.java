package com.catchtable.buddy.entity;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "buddies",
        indexes = {
                @Index(name = "idx_buddy_requester_id", columnList = "requester_id"),
                @Index(name = "idx_buddy_receiver_id",  columnList = "receiver_id"),
                @Index(name = "idx_buddy_requester_receiver", columnList = "requester_id, receiver_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Buddy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuddyStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Buddy(User requester, User receiver) {
        this.requester = requester;
        this.receiver = receiver;
        this.status = BuddyStatus.PENDING;
    }

    public void validateReceiver(Long userId) {
        if (!this.receiver.getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_BUDDY_REQUEST_RECEIVER);
        }
    }

    public void accept() {
        this.status = BuddyStatus.ACCEPTED;
    }

    public void reject() {
        this.status = BuddyStatus.REJECTED;
    }
}
