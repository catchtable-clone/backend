package com.catchtable.menu.dto.delete;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 삭제 응답")
public record MenuDeleteResponse(
        @Schema(description = "삭제된 메뉴 ID", example = "1")
        Long menuId,
        @Schema(description = "처리 결과 메시지", example = "메뉴 삭제 완료")
        String message
) {
}
