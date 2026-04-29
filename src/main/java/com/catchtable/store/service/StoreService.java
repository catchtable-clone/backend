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
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;

    // 매장 등록
    @Transactional
    public StoreCreateResponse createStore(Long userId, StoreCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_STORE_CREATE);
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
    }

    // 매장명 검색
    @Transactional(readOnly = true)
    public List<StoreListResponse> searchStores(String name) {
        List<Store> stores = storeRepository.searchByStoreName(name);
        return stores.stream()
                .map(StoreListResponse::from)
                .toList();
    }

    // 홈 노출용 상위 매장 조회 (전체 조회 방지)
    @Transactional(readOnly = true)
    public List<StoreListResponse> getPopularStores() {
        return storeRepository.findTop20ByIsDeletedFalseOrderByIdAsc().stream()
                .map(StoreListResponse::from)
                .toList();
    }

    // 지역별 매장 조회
    @Transactional(readOnly = true)
    public List<StoreListResponse> getStoresByDistrict(District district) {
        List<Store> stores = storeRepository.findAllByDistrictAndIsDeletedFalse(district);
        return stores.stream()
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
}
