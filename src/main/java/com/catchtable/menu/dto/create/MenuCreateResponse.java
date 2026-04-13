package com.catchtable.menu.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "메뉴 생성 응답")
public record MenuCreateResponse(
        @Schema(description = "생성된 메뉴 ID 목록", example = "[1, 2, 3]")
        List<Long> menuId
) {
}
