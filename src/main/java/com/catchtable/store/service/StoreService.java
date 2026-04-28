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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
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

    // 매장 목록 통합 조회 (이름·카테고리·지역 옵셔널 필터 + 페이지네이션)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStores(String name, Category category, District district, int page, int size) {
        String trimmedName = (name == null || name.isBlank()) ? null : name.trim();
        return storeRepository.findAllByIsDeletedFalse().stream()
                .filter(s -> trimmedName == null || s.getStoreName().contains(trimmedName))
                .filter(s -> category == null || s.getCategory() == category)
                .filter(s -> district == null || s.getDistrict() == district)
                .sorted(popularityComparator())
                .skip((long) page * size)
                .limit(size)
                .map(StoreListResponse::from)
                .toList();
    }

    // 인기 매장 (평균 평점 → 리뷰 수 → 북마크 수 순)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getPopularStores(int limit) {
        return storeRepository.findAllByIsDeletedFalse().stream()
                .sorted(popularityComparator())
                .limit(limit)
                .map(StoreListResponse::from)
                .toList();
    }

    /**
     * 매장 인기도 정렬 기준
     * 1. 평점 내림차순 → 2. 리뷰 수 내림차순 → 3. 북마크 수 내림차순
     */
    private Comparator<Store> popularityComparator() {
        return Comparator
                .comparingDouble((Store s) -> s.getAverageStar() != null ? s.getAverageStar() : 0.0)
                .thenComparingInt(Store::getReviewCount)
                .thenComparingInt(Store::getBookmarkCount)
                .reversed();
    }

    // 내 주변 매장 (좌표 거리 정렬 + 페이지네이션)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getNearbyStores(double latitude, double longitude, int page, int size) {
        return storeRepository.findAllByIsDeletedFalse().stream()
                .sorted(Comparator.comparingDouble(s ->
                        Math.pow(s.getLatitude() - latitude, 2) + Math.pow(s.getLongitude() - longitude, 2)
                ))
                .skip((long) page * size)
                .limit(size)
                .map(StoreListResponse::from)
                .toList();
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
