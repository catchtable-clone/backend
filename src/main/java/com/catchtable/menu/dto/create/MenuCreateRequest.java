package com.catchtable.menu.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "메뉴 일괄 생성 요청")
public record MenuCreateRequest(
        @Schema(description = "생성할 메뉴 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MenuItemRequest> menus
) {
    @Schema(description = "메뉴 항목")
    public record MenuItemRequest(
            @Schema(description = "메뉴 이름", example = "파스타", requiredMode = Schema.RequiredMode.REQUIRED)
            String menuName,

            @Schema(description = "가격 (원 단위)", example = "15000", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer price,

            @Schema(description = "메뉴 설명", example = "크림 파스타")
            String description
    ) {}
}
