package com.catchtable.bookmark.dto.folder.read;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 폴더 목록 조회 응답")
public record BookmarkFolderListResponse(
        @Schema(description = "폴더 ID", example = "1") Long folderId,
        @Schema(description = "폴더 이름", example = "가고 싶은 맛집") String folderName,
        @Schema(description = "폴더 색상 (HEX)", example = "#F97316") String color,
        @Schema(description = "폴더 타입 (DEFAULT | CUSTOM)", example = "CUSTOM") String folderType
) {
}
