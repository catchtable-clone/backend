package com.catchtable.review.repository;

import com.catchtable.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 해당 예약을 통해 이미 리뷰를 작성했는지 확인 (중복 리뷰 방지)
    boolean existsByReservationIdAndIsDeletedFalse(Long reservationId);
}
