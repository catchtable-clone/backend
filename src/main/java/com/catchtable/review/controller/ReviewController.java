package com.catchtable.review.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.review.dto.create.ReviewCreateRequestDto;

import com.catchtable.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReview(
            @RequestParam Long userId,
            @Valid @RequestBody ReviewCreateRequestDto request
    ) {
        Long reviewId = reviewService.createReview(userId, request);
        return ResponseEntity
                .status(SuccessCode.REVIEW_CREATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_CREATE_SUCCESS, reviewId));
    }
}