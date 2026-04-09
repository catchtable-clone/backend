package com.catchtable.coupon.entity;

import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_template_id", nullable = false)
    private CouponTemplate couponTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CouponStatus status = CouponStatus.UNUSED;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    public void use() {
        if (this.status != CouponStatus.UNUSED) {
            throw new IllegalArgumentException("사용 가능한 쿠폰이 아닙니다.");
        }
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
    }

    public void returnCoupon() {
        if (this.status != CouponStatus.USED) {
            throw new IllegalArgumentException("사용된 쿠폰만 반환할 수 있습니다.");
        }
        this.status = CouponStatus.UNUSED;
        this.usedAt = null;
    }
}
