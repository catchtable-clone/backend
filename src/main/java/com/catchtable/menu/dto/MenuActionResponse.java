package com.catchtable.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 수정/삭제 응답")
public record MenuActionResponse(
        @Schema(description = "처리된 메뉴 ID", example = "1") 
        Long menuId,
        @Schema(description = "처리 결과 메시지", example = "메뉴가 성공적으로 수정되었습니다.") 
        String message
) {
}
