package com.catchtable.store.service;

import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.remain.dto.read.RemainDateResponse;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.review.repository.ReviewRepository;
import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final ReviewRepository reviewRepository;

    // 매장 등록
    @Transactional
    public StoreCreateResponse createStore(Long userId, StoreCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_CREATE);
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
    }

    /**
     * 매장 목록 통합 조회 (이름·카테고리·지역 옵셔널 필터 + DB 페이지네이션 + 인기 정렬)
     * Specification 사용으로 PostgreSQL+enum 조합에서 :param IS NULL 회피.
     */
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStores(String name, Category category, District district, int page, int size) {
        String trimmedName = (name == null || name.isBlank()) ? null : name.trim();
        Specification<Store> spec = buildStoreSpec(trimmedName, category, district);
        Pageable pageable = PageRequest.of(page, size, popularitySort());
        return storeRepository.findAll(spec, pageable).getContent().stream()
                .map(StoreListResponse::from)
                .toList();
    }

    // 인기 매장 (averageStar DESC → reviewCount DESC → bookmarkCount DESC) — DB 정렬 + LIMIT
    @Transactional(readOnly = true)
    public List<StoreListResponse> getPopularStores(int limit) {
        return storeRepository.findPopular(PageRequest.of(0, limit)).stream()
                .map(StoreListResponse::from)
                .toList();
    }

    // 내 주변 매장 (좌표 거리 정렬 + DB 페이지네이션)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getNearbyStores(double latitude, double longitude, int page, int size) {
        return storeRepository.findNearby(latitude, longitude, PageRequest.of(page, size)).stream()
                .map(StoreListResponse::from)
                .toList();
    }

    /**
     * 지도 화면 영역 안의 매장을 화면 중심에서 가까운 순서로 조회.
     * limit를 넘으면 가장 먼 매장이 잘려나가므로 화면 중앙 주변에 마커가 균등하게 분포한다.
     * 화면 중심 좌표가 누락되면 영역의 기하 중심을 자동 계산해 사용한다.
     */
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStoresInBounds(
            double minLat, double maxLat, double minLng, double maxLng,
            Double centerLat, Double centerLng, int limit) {
        double cLat = centerLat != null ? centerLat : (minLat + maxLat) / 2.0;
        double cLng = centerLng != null ? centerLng : (minLng + maxLng) / 2.0;
        return storeRepository
                .findInBounds(minLat, maxLat, minLng, maxLng, cLat, cLng, PageRequest.of(0, limit))
                .stream()
                .map(StoreListResponse::from)
                .toList();
    }

    /**
     * 매장 인기도 정렬 기준
     * 1. 평점 내림차순 → 2. 리뷰 수 내림차순 → 3. 북마크 수 내림차순
     * 4. (타이브레이커) id 오름차순 — 동률 시 먼저 등록된 매장이 위로 오도록 결정성 보장
     */
    private Sort popularitySort() {
        return Sort.by(
                Sort.Order.desc("averageStar"),
                Sort.Order.desc("reviewCount"),
                Sort.Order.desc("bookmarkCount"),
                Sort.Order.asc("id")
        );
    }

    /**
     * 이름(LIKE) + 카테고리/지역(EQ) + isDeleted=false 필터를 Specification으로 조립.
     */
    private Specification<Store> buildStoreSpec(String name, Category category, District district) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (name != null) {
                predicates.add(cb.like(cb.lower(root.get("storeName")),
                        "%" + name.toLowerCase() + "%"));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (district != null) {
                predicates.add(cb.equal(root.get("district"), district));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 매장 상세조회 + 예약 가능 시간대 조회
    @Transactional(readOnly = true)
    public StoreDetailResponse getStore(Long storeId) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        List<RemainDateResponse> remainDates = storeRemainRepository
                .findDateAvailabilityByStoreId(storeId, LocalDate.now())
                .stream()
                .map(row -> new RemainDateResponse(
                        (LocalDate) row[0],
                        ((Long) row[1]) > 0
                ))
                .toList();

        return StoreDetailResponse.from(store, remainDates);
    }

    // 매장 정보 수정
    @Transactional
    public StoreUpdateResponse updateStore(Long userId, Long storeId, StoreUpdateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_UPDATE);
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.update(
                request.storeName(), request.storeImage(), request.category(),
                request.latitude(), request.longitude(), request.address(),
                request.district(), request.team(), request.openTime(), request.closeTime()
        );
        return StoreUpdateResponse.from(store);
    }

    // 매장 상태 변경
    @Transactional
    public StoreStatusUpdateResponse updateStoreStatus(Long userId, Long storeId, StoreStatusUpdateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_STATUS);
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.changeStatus(request.status());
        return StoreStatusUpdateResponse.from(store.getId(), store.getStatus().name());
    }

    @Transactional
    public void increaseReviewCount(Long storeId) {
        storeRepository.increaseReviewCount(storeId);
    }

    @Transactional
    public void decreaseReviewCount(Long storeId) {
        storeRepository.decreaseReviewCount(storeId);
    }

    /**
     * 매장 평균 평점 재계산 (리뷰 등록·수정·삭제 시 호출)
     */
    @Transactional
    public void recalculateAverageStar(Long storeId) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        Double newAverage = reviewRepository.findAverageStarByStoreId(storeId);
        store.updateAverageStar(newAverage);
    }
}
