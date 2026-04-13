package com.catchtable.bookmark.dto.folder.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "북마크 폴더 생성 요청")
public record BookmarkFolderCreateRequest(
        @NotBlank @Schema(description = "폴더 이름", example = "가고 싶은 맛집") String folderName
) {
}
