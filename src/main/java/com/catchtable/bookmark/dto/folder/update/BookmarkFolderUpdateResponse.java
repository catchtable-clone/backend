package com.catchtable.bookmark.dto.folder.update;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 폴더 수정 응답")
public record BookmarkFolderUpdateResponse(
        @Schema(description = "수정된 폴더 ID", example = "1") Long folderId,
        @Schema(description = "수정된 폴더 이름", example = "자주 가는 맛집") String folderName,
        @Schema(description = "수정된 폴더 색상 (HEX)", example = "#3B82F6") String color
) {
}
