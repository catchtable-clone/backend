package com.catchtable.review.dto.response;

import com.catchtable.review.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponseDto(
        Long reviewId,
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
                review.getUser().getId(),
                review.getUser().getNickname(),
                review.getStar(),
                review.getContent(),
                review.getReviewImage(),
                review.getCreatedAt()
        );
    }
}
