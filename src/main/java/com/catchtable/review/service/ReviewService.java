package com.catchtable.review.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import com.catchtable.review.dto.create.ReviewCreateRequestDto;

import com.catchtable.review.dto.read.ReviewResponseDto;
import com.catchtable.review.dto.update.ReviewUpdateRequestDto;
import com.catchtable.review.entity.Review;
import com.catchtable.review.event.ReviewCreatedEvent;
import com.catchtable.review.event.ReviewDeletedEvent;
import com.catchtable.review.event.ReviewUpdatedEvent;
import com.catchtable.review.repository.ReviewRepository;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long createReview(Long userId, ReviewCreateRequestDto request) {
        User user = userRepository.getById(userId);

        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        // 예약자 검증
        reservation.validateOwner(userId);

        // 예약 상태 VISITED 인지 확인
        if (reservation.getStatus() != ReservationStatus.VISITED) {
            throw new CustomException(ErrorCode.REVIEW_NOT_ALLOWED);
        }

        // 중복 작성 검증
        if (reviewRepository.existsByReservationIdAndIsDeletedFalse(reservation.getId())) {
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 리뷰 저장 시 해당 매장 정보도 저장
        Store store = reservation.getStoreRemain().getStore();

        Review review = Review.builder()
                .user(user)
                .store(store)
                .reservation(reservation)
                .star(request.star())
                .content(request.content())
                .reviewImage(request.reviewImage())
                .build();

        reviewRepository.save(review);

        // 리뷰 카운트 + 평균 평점 갱신은 트랜잭션 commit 후 비동기로 처리한다
        eventPublisher.publishEvent(new ReviewCreatedEvent(store.getId(), request.star()));

        return review.getId();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getStoreReviews(Long storeId) {
        if (!storeRepository.existsById(storeId)) {
            throw new CustomException(ErrorCode.STORE_NOT_FOUND);
        }

        List<Review> reviews = reviewRepository.findAllByStoreIdAndIsDeletedFalseOrderByCreatedAtDesc(storeId);
        return reviews.stream()
                .map(ReviewResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getMyReviews(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        List<Review> reviews = reviewRepository.findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
        return reviews.stream()
                .map(ReviewResponseDto::from)
                .toList();
    }

    @Transactional
    public Long updateReview(Long userId, Long reviewId, ReviewUpdateRequestDto request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        review.validateOwner(userId);

        if (review.getIsDeleted()) {
            throw new CustomException(ErrorCode.REVIEW_NOT_FOUND); // 이미 삭제된 리뷰
        }

        Integer oldStar = review.getStar();
        review.updateReview(request.star(), request.content(), request.reviewImage());

        // 별점이 변경된 경우 매장 평균 평점 비동기 갱신
        if (request.star() != null && !request.star().equals(oldStar)) {
            eventPublisher.publishEvent(new ReviewUpdatedEvent(
                    review.getStore().getId(), oldStar, request.star()));
        }
        return review.getId();
    }

    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        review.validateOwner(userId);

        if (review.getIsDeleted()) {
            throw new CustomException(ErrorCode.REVIEW_NOT_FOUND);
        }

        Long storeId = review.getStore().getId();
        int deletedStar = review.getStar();
        review.delete();

        // 리뷰 카운트 감소 + 평균 평점 비동기 갱신
        eventPublisher.publishEvent(new ReviewDeletedEvent(storeId, deletedStar));
    }
}
