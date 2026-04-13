package com.catchtable.bookmark.dto.folder.create;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 폴더 생성 응답")
public record BookmarkFolderCreateResponse(
        @Schema(description = "생성된 폴더 ID", example = "1") Long folderId
) {
}
