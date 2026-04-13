package com.catchtable.store.service;

import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.entity.UserRole;
import com.catchtable.user.repository.UserRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public StoreCreateResponse createStore(Long userId, StoreCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_ONLY_STORE_CREATE);
        }
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
    }

    @Transactional
    public StoreUpdateResponse updateStore(Long userId, Long storeId, StoreUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_ONLY_STORE_UPDATE);
        }
        Store store = storeRepository.findById(storeId)
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.update(
                request.getStoreName(), request.getStoreImage(), request.getCategory(),
                request.getLatitude(), request.getLongitude(), request.getAddress(),
                request.getDistrict(), request.getTeam(), request.getOpenTime(), request.getCloseTime()
        );
        return StoreUpdateResponse.from(store);
    }

    @Transactional
    public StoreStatusUpdateResponse updateStoreStatus(Long userId, Long storeId, StoreStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.ADMIN_ONLY_STORE_STATUS);
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        store.changeStatus(request.getStatus());
        return StoreStatusUpdateResponse.from(store.getId(), store.getStatus().name());
    }

    @Transactional(readOnly = true)
    public List<StoreListResponse> searchStores(String name) {
        List<Store> stores = storeRepository.searchByStoreName(name);
        return stores.stream()
                .map(StoreListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreDetailResponse getStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));
        return StoreDetailResponse.from(store);
    }
}
