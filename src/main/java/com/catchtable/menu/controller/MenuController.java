package com.catchtable.menu.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.menu.dto.create.MenuCreateRequest;
import com.catchtable.menu.dto.create.MenuCreateResponse;
import com.catchtable.menu.dto.delete.MenuDeleteResponse;
import com.catchtable.menu.dto.read.MenuResponse;
import com.catchtable.menu.dto.update.MenuUpdateRequest;
import com.catchtable.menu.dto.update.MenuUpdateResponse;
import com.catchtable.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "메뉴 컨트롤러", description = "가게 메뉴 관련 API")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "메뉴 일괄 생성", description = "특정 가게에 메뉴를 한 번에 여러 개 생성합니다. 관리자 전용.")
    @PostMapping
    public ResponseEntity<ApiResponse<MenuCreateResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "가게 ID", required = true) @PathVariable Long storeId,
            @Valid @RequestBody MenuCreateRequest request
    ) {
        MenuCreateResponse response = menuService.create(userId, storeId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.MENU_CREATED, response));
    }

    @Operation(summary = "가게 메뉴 목록 조회", description = "특정 가게의 삭제되지 않은 메뉴 전체를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getByStoreId(
            @Parameter(description = "가게 ID", required = true) @PathVariable Long storeId
    ) {
        List<MenuResponse> response = menuService.getByStoreId(storeId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.MENU_LIST_OK, response));
    }

    @Operation(summary = "메뉴 수정", description = "특정 메뉴의 이름, 가격, 설명, 이미지를 수정합니다. 관리자 전용 + 매장 소속 검증.")
    @PatchMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuUpdateResponse>> update(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "가게 ID", required = true) @PathVariable Long storeId,
            @Parameter(description = "메뉴 ID", required = true) @PathVariable Long menuId,
            @Valid @RequestBody MenuUpdateRequest request
    ) {
        MenuUpdateResponse response = menuService.update(userId, storeId, menuId, request);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.MENU_UPDATED, response));
    }

    @Operation(summary = "메뉴 삭제", description = "특정 메뉴를 소프트 삭제합니다. 관리자 전용 + 매장 소속 검증.")
    @DeleteMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuDeleteResponse>> delete(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "가게 ID", required = true) @PathVariable Long storeId,
            @Parameter(description = "메뉴 ID", required = true) @PathVariable Long menuId
    ) {
        MenuDeleteResponse response = menuService.delete(userId, storeId, menuId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.MENU_DELETED, response));
    }
}
