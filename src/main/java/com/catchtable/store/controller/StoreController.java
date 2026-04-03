package com.catchtable.store.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.store.dto.StoreCreateRequest;
import com.catchtable.store.dto.StoreCreateResponse;
import com.catchtable.store.dto.StoreDetailResponse;
import com.catchtable.store.dto.StoreListResponse;
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
            @Valid @RequestBody StoreCreateRequest request) {
        StoreCreateResponse response = storeService.createStore(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "매장이 등록되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> searchStores(
            @RequestParam String name) {
        List<StoreListResponse> stores = storeService.searchStores(name);
        return ResponseEntity
                .ok(ApiResponse.success(200, "success", stores));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreDetailResponse>> getStore(
            @PathVariable Long storeId) {
        StoreDetailResponse store = storeService.getStore(storeId);
        return ResponseEntity
                .ok(ApiResponse.success(200, "success", store));
    }
}
