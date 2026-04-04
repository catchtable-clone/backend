package com.catchtable.store.service;

import com.catchtable.store.dto.StoreCreateRequest;
import com.catchtable.store.dto.StoreCreateResponse;
import com.catchtable.store.dto.StoreDetailResponse;
import com.catchtable.store.dto.StoreListResponse;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;

    @Transactional
    public StoreCreateResponse createStore(StoreCreateRequest request) {
        Store store = request.toEntity();
        Store saved = storeRepository.save(store);
        return StoreCreateResponse.from(saved.getId());
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
                .orElseThrow(() -> new IllegalArgumentException("매장을 찾을 수 없습니다."));
        return StoreDetailResponse.from(store);
    }
}
