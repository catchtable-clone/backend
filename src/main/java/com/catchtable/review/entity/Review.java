package com.catchtable.review.entity;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.store.entity.Store;
import com.catchtable.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    // 하나의 예약당 하나의 리뷰만 작성 가능
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private Integer star; // 별점

    @Column(nullable = false, length = 1000)
    private String content; // 리뷰 내용

    @Column(name = "review_image")
    private String reviewImage; // 리뷰 이미지

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Builder
    public Review(User user, Store store, Reservation reservation, Integer star, String content, String reviewImage) {
        this.user = user;
        this.store = store;
        this.reservation = reservation;
        this.star = star;
        this.content = content;
        this.reviewImage = reviewImage;
        this.isDeleted = false;
    }

    public void delete() {
        this.isDeleted = true;
    }

    public void updateReview(Integer star, String content, String reviewImage) {
        if (star != null) {
            this.star = star;
        }
        if (content != null) {
            this.content = content;
        }
        
        // 이미지가 있는 리뷰에서 수정하여 이미지를 삭제하고 싶을 때는 빈 문자열("")을 보내기
        // null이 들어오면 기존 이미지를 유지하고 빈 문자열이 들어오면 null로 초기화하여 이미지를 삭제
        if (reviewImage != null) {
            if (reviewImage.isBlank()) {
                this.reviewImage = null; // 이미지 삭제
            } else {
                this.reviewImage = reviewImage; // 새 이미지로 교체
            }
        }
    }
}
