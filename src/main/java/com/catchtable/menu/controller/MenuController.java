package com.catchtable.menu.controller;

import com.catchtable.menu.dto.MenuActionResponse;
import com.catchtable.menu.dto.MenuCreateResponse;
import com.catchtable.menu.dto.MenuResponse;
import com.catchtable.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "메뉴", description = "가게 메뉴 관련 API")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(
        summary = "메뉴 일괄 생성",
        description = "특정 가게에 메뉴를 한 번에 여러 개 생성합니다. menuNames, prices는 같은 인덱스끼리 매핑됩니다."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MenuCreateResponse> create(

            @Parameter(description = "가게 ID", required = true) 
            @PathVariable 
            Long storeId,

            @Parameter(description = "메뉴 이름 목록", required = true) 
            @RequestParam 
            List<String> menuNames,

            @Parameter(description = "메뉴 이미지 파일 목록 (선택)") 
            @RequestParam(required = false) 
            List<MultipartFile> menuImages,

            @Parameter(description = "가격 목록 (원 단위)", required = true) 
            @RequestParam 
            List<Integer> prices,

            @Parameter(description = "메뉴 설명 목록 (선택)") 
            @RequestParam(required = false) 
            List<String> descriptions
        ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuService.create(storeId, menuNames, menuImages, prices, descriptions));
    }

    @Operation(
        summary = "가게 메뉴 목록 조회",
        description = "특정 가게의 삭제되지 않은 메뉴 전체를 조회합니다."
    )
    @GetMapping
    public ResponseEntity<List<MenuResponse>> getByStoreId(
            @Parameter(description = "가게 ID", required = true) 
            @PathVariable 
            Long storeId
        ) {
        return ResponseEntity.ok(menuService.getByStoreId(storeId));
    }

    @Operation(
        summary = "메뉴 수정",
        description = "특정 메뉴의 이름, 가격, 설명, 이미지를 수정합니다."
    )
    @PatchMapping(value = "/{menuId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MenuActionResponse> update(
            @Parameter(description = "가게 ID", required = true) 
            @PathVariable 
            Long storeId,

            @Parameter(description = "메뉴 ID", required = true) 
            @PathVariable 
            Long menuId,

            @Parameter(description = "메뉴 이름", required = true) 
            @RequestParam 
            String menuName,

            @Parameter(description = "메뉴 이미지 파일 (선택)") 
            @RequestParam(required = false) 
            MultipartFile menuImage,

            @Parameter(description = "가격 (원 단위)", required = true) 
            @RequestParam 
            Integer price,

            @Parameter(description = "메뉴 설명 (선택)") 
            @RequestParam(required = false) 
            String description
        ) {
        return ResponseEntity.ok(menuService.update(menuId, menuName, menuImage, price, description));
    }

    @Operation(
        summary = "메뉴 삭제",
        description = "특정 메뉴를 소프트 삭제합니다. 실제 DB에서 삭제되지 않고 삭제 플래그만 변경됩니다."
    )
    @DeleteMapping("/{menuId}")
    public ResponseEntity<MenuActionResponse> delete(
            @Parameter(description = "가게 ID", required = true) 
            @PathVariable 
            Long storeId,

            @Parameter(description = "메뉴 ID", required = true) 
            @PathVariable 
            Long menuId
        ) {
        return ResponseEntity.ok(menuService.delete(menuId));
    }
}
