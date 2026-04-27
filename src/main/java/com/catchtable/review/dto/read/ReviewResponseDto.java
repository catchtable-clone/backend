package com.catchtable.review.dto.read;

import com.catchtable.review.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponseDto(
        Long reviewId,
        Long reservationId,
        Long storeId,
        String storeName,
        Long userId,
        String userNickname,
        Integer star,
        String content,
        String reviewImage,
        LocalDateTime createdAt
) {
    public static ReviewResponseDto from(Review review) {
        return new ReviewResponseDto(
                review.getId(),
                review.getReservation().getId(),
                review.getStore().getId(),
                review.getStore().getStoreName(),
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getStar(),
                review.getContent(),
                review.getReviewImage(),
                review.getCreatedAt()
        );
    }
}
