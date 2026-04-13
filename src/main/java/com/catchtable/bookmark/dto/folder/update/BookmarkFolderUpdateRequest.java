package com.catchtable.bookmark.dto.folder.update;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "북마크 폴더 이름 수정 요청")
public record BookmarkFolderUpdateRequest(
        @NotBlank @Schema(description = "변경할 폴더 이름", example = "자주 가는 맛집") String folderName
) {
}
