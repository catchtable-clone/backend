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
import com.catchtable.global.exception.AccessDeniedException;
import com.catchtable.global.exception.ResourceNotFoundException;
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
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("관리자만 매장을 등록할 수 있습니다.");
        }
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
    }

    @Transactional
    public StoreUpdateResponse updateStore(Long userId, Long storeId, StoreUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("관리자만 매장을 수정할 수 있습니다.");
        }
        Store store = storeRepository.findById(storeId)
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("매장을 찾을 수 없습니다."));
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
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("관리자만 매장 상태를 변경할 수 있습니다.");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("매장을 찾을 수 없습니다."));
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
                .orElseThrow(() -> new ResourceNotFoundException("매장을 찾을 수 없습니다."));
        return StoreDetailResponse.from(store);
    }
}
