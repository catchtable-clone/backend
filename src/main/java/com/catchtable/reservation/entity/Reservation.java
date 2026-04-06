package com.catchtable.reservation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reservationId; //예약 아이디

    private Long userId; //예약자

    private Long remainId; //남은 테이블 아이디

    private Integer member; //예약 인원

    @Enumerated(EnumType.STRING)
    private ReservationStatus status; //예약 상태

    private LocalDateTime createdAt; //생성

    private LocalDateTime updatedAt; //수정

    @Builder
    public Reservation(Long userId, Long remainId, Integer member, ReservationStatus status) {
        this.userId = userId;
        this.remainId = remainId;
        this.member = member;
        this.status = status;
    }

    @PrePersist
    protected void Create() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = ReservationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void Update() {
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(ReservationStatus status) {
        this.status = status;
    }
}
