package com.catchtable.review.repository;

import com.catchtable.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 특정 매장의 삭제되지 않은 모든 리뷰 조회 (가장 최근 작성된 순)
    @Query("SELECT r FROM Review r JOIN FETCH r.user WHERE r.store.id = :storeId AND r.isDeleted = false ORDER BY r.createdAt DESC")
    List<Review> findAllByStoreIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("storeId") Long storeId);

    // 특정 사용자가 작성한 삭제되지 않은 모든 리뷰 조회
    @Query("SELECT r FROM Review r JOIN FETCH r.store WHERE r.user.id = :userId AND r.isDeleted = false ORDER BY r.createdAt DESC")
    List<Review> findAllByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(@Param("userId") Long userId);

    // 해당 예약을 통해 이미 리뷰를 작성했는지 확인 (중복 리뷰 방지)
    boolean existsByReservationIdAndIsDeletedFalse(Long reservationId);
}
