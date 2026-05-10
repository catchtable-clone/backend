package com.catchtable.menu.dto.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "메뉴 수정 요청")
public record MenuUpdateRequest(
        @Schema(description = "메뉴 이름", example = "파스타", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "메뉴 이름은 필수입니다.")
        String menuName,

        @Schema(description = "가격 (원 단위)", example = "15000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        @Schema(description = "메뉴 설명", example = "크림 파스타")
        String description,

        @Schema(description = "메뉴 이미지 URL (선택)", example = "http://localhost:8080/uploads/abc.jpg")
        String menuImage
) {
}
