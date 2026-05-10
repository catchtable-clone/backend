package com.catchtable.vacancy.entity;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vacancy_subscriptions")
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remain_id", nullable = false)
    private StoreRemain storeRemain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacancyStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    public Vacancy(User user, StoreRemain storeRemain) {
        this.user = user;
        this.storeRemain = storeRemain;
        this.status = VacancyStatus.ACTIVE;
        this.isDeleted = false;
    }

    public void delete() {
        this.isDeleted = true;
    }

    public void markNotified() {
        this.status = VacancyStatus.NOTIFIED;
    }
}
