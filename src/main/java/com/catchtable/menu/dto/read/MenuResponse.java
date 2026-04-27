package com.catchtable.menu.dto.read;

import com.catchtable.menu.entity.Menu;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메뉴 조회 응답")
public record MenuResponse(
        @Schema(description = "메뉴 ID", example = "1")
        Long menuId,
        @Schema(description = "메뉴 이름", example = "된장찌개")
        String menuName,
        @Schema(description = "메뉴 이미지 파일명", example = "doenjang.jpg")
        String menuImage,
        @Schema(description = "가격 (원 단위)", example = "9000")
        Integer price,
        @Schema(description = "메뉴 설명", example = "구수한 된장찌개")
        String description
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getId(),
                menu.getMenuName(),
                menu.getMenuImage(),
                menu.getPrice(),
                menu.getDescription()
        );
    }
}
