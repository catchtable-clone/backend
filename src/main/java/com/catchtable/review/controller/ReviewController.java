package com.catchtable.review.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import com.catchtable.review.dto.create.ReviewCreateRequestDto;
import com.catchtable.review.dto.read.ReviewResponseDto;
import com.catchtable.review.dto.update.ReviewUpdateRequestDto;
import com.catchtable.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReviewCreateRequestDto request
    ) {
        Long reviewId = reviewService.createReview(userDetails.getUserId(), request);
        return ResponseEntity
                .status(SuccessCode.REVIEW_CREATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_CREATE_SUCCESS, reviewId));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getStoreReviews(
            @PathVariable Long storeId
    ) {
        List<ReviewResponseDto> responseData = reviewService.getStoreReviews(storeId);
        return ResponseEntity
                .status(SuccessCode.REVIEW_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_LOOKUP_SUCCESS, responseData));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReviewResponseDto>>> getMyReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ReviewResponseDto> responseData = reviewService.getMyReviews(userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.REVIEW_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_LOOKUP_SUCCESS, responseData));
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Long>> updateReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReviewUpdateRequestDto request
    ) {
        Long updatedReviewId = reviewService.updateReview(userDetails.getUserId(), reviewId, request);
        return ResponseEntity
                .status(SuccessCode.REVIEW_UPDATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_UPDATE_SUCCESS, updatedReviewId));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        reviewService.deleteReview(userDetails.getUserId(), reviewId);
        return ResponseEntity
                .status(SuccessCode.REVIEW_DELETE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.REVIEW_DELETE_SUCCESS));
    }
}
