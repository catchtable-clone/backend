package com.catchtable.store.repository;

import com.catchtable.store.entity.Store;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * 좌표 거리 기반 정렬 — PostGIS ST_DistanceSphere 사용 (구면 거리, 미터 단위).
     * Euclidean(좌표 차이 제곱) 방식은 지구 곡률을 무시해 위도가 다를수록 오차가 커지므로,
     * 실제 운영 데이터에서는 부정확한 정렬이 나올 수 있다.
     * 거리가 같으면 ID 오름차순으로 결정성 보장.
     *
     * NOTE: Native query — H2 등 PostGIS 미지원 DB에서는 동작하지 않으므로 통합 테스트 시 주의.
     */
    @Query(value = """
            SELECT * FROM stores s
            WHERE s.is_deleted = false
            ORDER BY ST_DistanceSphere(
                       ST_MakePoint(s.longitude, s.latitude),
                       ST_MakePoint(:lon, :lat)
                     ) ASC,
                     s.id ASC
            """,
           nativeQuery = true)
    List<Store> findNearby(@Param("lat") double latitude,
                           @Param("lon") double longitude,
                           Pageable pageable);

    /**
     * 지도 화면 영역(bounding box) 안의 매장을 화면 중심에서 가까운 순서로 검색.
     * limit를 넘기면 멀리 있는 매장이 잘려나가므로, 분포가 화면 중앙 주변에 균등하게 모인다.
     * 거리 계산은 PostGIS ST_DistanceSphere 사용.
     */
    @Query(value = """
            SELECT * FROM stores s
            WHERE s.is_deleted = false
              AND s.latitude  BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
            ORDER BY ST_DistanceSphere(
                       ST_MakePoint(s.longitude, s.latitude),
                       ST_MakePoint(:centerLng, :centerLat)
                     ) ASC,
                     s.id ASC
            """,
           nativeQuery = true)
    List<Store> findInBounds(@Param("minLat") double minLat,
                             @Param("maxLat") double maxLat,
                             @Param("minLng") double minLng,
                             @Param("maxLng") double maxLng,
                             @Param("centerLat") double centerLat,
                             @Param("centerLng") double centerLng,
                             Pageable pageable);

}
