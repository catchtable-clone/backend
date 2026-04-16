package com.catchtable.reservation.entity;

import java.time.LocalDateTime;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 예약자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remain_id", nullable = false)
    private StoreRemain storeRemain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    private Integer member; //예약 인원

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean reminded = false;

    @Builder
    public Reservation(User user, StoreRemain storeRemain, Coupon coupon, Integer member, ReservationStatus status) {
        this.user = user;
        this.storeRemain = storeRemain;
        this.coupon = coupon;
        this.member = member;
        this.status = status;
    }

    @PrePersist
    protected void Create() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = ReservationStatus.CONFIRMED;
        }
    }

    @PreUpdate
    protected void Update() {
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(ReservationStatus status) {
        this.status = status;
    }

    public void markReminded() {
        this.reminded = true;
    }
}
