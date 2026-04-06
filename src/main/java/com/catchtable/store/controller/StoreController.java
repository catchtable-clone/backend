package com.catchtable.store.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.store.dto.create.StoreCreateRequest;
import com.catchtable.store.dto.create.StoreCreateResponse;
import com.catchtable.store.dto.read.StoreDetailResponse;
import com.catchtable.store.dto.read.StoreListResponse;
import com.catchtable.store.dto.status.StoreStatusUpdateRequest;
import com.catchtable.store.dto.status.StoreStatusUpdateResponse;
import com.catchtable.store.dto.update.StoreUpdateRequest;
import com.catchtable.store.dto.update.StoreUpdateResponse;
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
                .body(ApiResponse.success(201, "매장이 등록되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> searchStores(
            @RequestParam String name) {
        List<StoreListResponse> stores = storeService.searchStores(name);
        return ResponseEntity
                .ok(ApiResponse.success(200, "매장 목록을 조회했습니다.", stores));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> getStore(
            @PathVariable Long storeId) {
        StoreDetailResponse store = storeService.getStore(storeId);
        return ResponseEntity
                .ok(ApiResponse.success(200, "매장 정보를 조회했습니다.", store));
    }

    @PutMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreUpdateResponse>> updateStore(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreUpdateRequest request) {
        StoreUpdateResponse response = storeService.updateStore(userId, storeId, request);
        return ResponseEntity
                .ok(ApiResponse.success(200, "매장 정보가 수정되었습니다.", response));
    }

    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreStatusUpdateResponse>> updateStoreStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreStatusUpdateRequest request) {
        StoreStatusUpdateResponse response = storeService.updateStoreStatus(userId, storeId, request);
        return ResponseEntity
                .ok(ApiResponse.success(200, "매장 상태가 변경되었습니다.", response));
    }
}
