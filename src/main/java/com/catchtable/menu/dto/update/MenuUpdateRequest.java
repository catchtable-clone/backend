package com.catchtable.menu.dto.update;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 수정 요청")
public record MenuUpdateRequest(
        @Schema(description = "메뉴 이름", example = "파스타", requiredMode = Schema.RequiredMode.REQUIRED)
        String menuName,

        @Schema(description = "가격 (원 단위)", example = "15000", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer price,

        @Schema(description = "메뉴 설명", example = "크림 파스타")
        String description
) {
}
