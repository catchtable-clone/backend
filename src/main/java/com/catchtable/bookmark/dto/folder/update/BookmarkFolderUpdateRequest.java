package com.catchtable.bookmark.dto.folder.update;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 폴더 수정 요청 — 이름·색상은 각각 선택적으로 갱신 가능")
public record BookmarkFolderUpdateRequest(
        @Schema(description = "변경할 폴더 이름", example = "자주 가는 맛집") String folderName,
        @Schema(description = "변경할 폴더 색상 (HEX)", example = "#3B82F6") String color
) {
}
