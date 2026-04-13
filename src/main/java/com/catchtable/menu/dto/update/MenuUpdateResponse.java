package com.catchtable.menu.dto.update;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 수정 응답")
public record MenuUpdateResponse(
        @Schema(description = "수정된 메뉴 ID", example = "1")
        Long menuId,
        @Schema(description = "처리 결과 메시지", example = "메뉴 수정 완료")
        String message
) {
}
