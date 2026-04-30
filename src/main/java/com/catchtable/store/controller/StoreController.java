package com.catchtable.store.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
import com.catchtable.store.entity.Category;
import com.catchtable.store.entity.District;
import com.catchtable.store.service.StoreService;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoreCreateResponse>> createStore(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody StoreCreateRequest request) {
        StoreCreateResponse response = storeService.createStore(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.STORE_CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> getStores(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) District district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<StoreListResponse> stores = storeService.getStores(name, category, district, page, size);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_LIST_OK, stores));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> getPopularStores(
            @RequestParam(defaultValue = "10") int limit) {
        List<StoreListResponse> stores = storeService.getPopularStores(limit);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_LIST_OK, stores));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> getNearbyStores(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<StoreListResponse> stores = storeService.getNearbyStores(latitude, longitude, page, size);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_LIST_OK, stores));
    }

    /**
     * 지도 화면 영역(bounding box) 안의 매장 조회.
     * 줌/팬 시 호출되어 현재 화면에 보이는 매장만 마커로 그린다.
     * limit는 줌 아웃으로 전국이 잡혀도 응답이 폭주하지 않도록 상한선을 둔다.
     */
    @GetMapping("/in-bounds")
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> getStoresInBounds(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng,
            @RequestParam(required = false) Double centerLat,
            @RequestParam(required = false) Double centerLng,
            @RequestParam(defaultValue = "1000") int limit) {
        List<StoreListResponse> stores = storeService.getStoresInBounds(
                minLat, maxLat, minLng, maxLng, centerLat, centerLng, limit);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_LIST_OK, stores));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> getStore(
            @PathVariable Long storeId) {
        StoreDetailResponse store = storeService.getStore(storeId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_DETAIL_OK, store));
    }

    @PutMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreUpdateResponse>> updateStore(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreUpdateRequest request) {
        StoreUpdateResponse response = storeService.updateStore(userId, storeId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_UPDATED, response));
    }

    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreStatusUpdateResponse>> updateStoreStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreStatusUpdateRequest request) {
        StoreStatusUpdateResponse response = storeService.updateStoreStatus(userId, storeId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.STORE_STATUS_UPDATED, response));
    }
}
