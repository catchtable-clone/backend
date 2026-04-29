package com.catchtable.store.repository;

import com.catchtable.store.entity.Store;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {

    Optional<Store> findByIdAndIsDeletedFalse(Long id);

    List<Store> findAllByIsDeletedFalse();

    /**
     * 인기 매장 정렬 — averageStar DESC → reviewCount DESC → bookmarkCount DESC → id ASC
     * - averageStar는 NULL일 수 있어 COALESCE로 0 처리
     * - 모든 통계 값이 동률일 때 ID 오름차순(=먼저 등록된 순)으로 결정성 보장
     */
    @Query("""
            SELECT s FROM Store s
            WHERE s.isDeleted = false
            ORDER BY COALESCE(s.averageStar, 0) DESC,
                     s.reviewCount DESC,
                     s.bookmarkCount DESC,
                     s.id ASC
            """)
    List<Store> findPopular(Pageable pageable);

    /**
     * 좌표 거리 기반 정렬 (제곱 거리 사용 — sqrt 생략)
     * 거리가 같으면 ID 오름차순으로 결정성 보장
     */
    @Query("""
            SELECT s FROM Store s
            WHERE s.isDeleted = false
            ORDER BY ((s.latitude - :lat) * (s.latitude - :lat)
                   + (s.longitude - :lon) * (s.longitude - :lon)) ASC,
                     s.id ASC
            """)
    List<Store> findNearby(@Param("lat") double latitude,
                           @Param("lon") double longitude,
                           Pageable pageable);

    /**
     * 리뷰 수 증감은 벌크 UPDATE이므로 영속성 컨텍스트와 DB가 일치하도록
     * 호출 전 flush, 호출 후 clear 한다.
     * 이로써 같은 트랜잭션 내 후속 findByIdAndIsDeletedFalse() 가 stale 캐시를 반환하지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Store s SET s.reviewCount = s.reviewCount + 1 WHERE s.id = :storeId")
    void increaseReviewCount(@Param("storeId") Long storeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Store s SET s.reviewCount = s.reviewCount - 1 WHERE s.id = :storeId AND s.reviewCount > 0")
    void decreaseReviewCount(@Param("storeId") Long storeId);
}
