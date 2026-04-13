package com.catchtable.bookmark.dto.folder.delete;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 폴더 삭제 응답")
public record BookmarkFolderDeleteResponse(
        @Schema(description = "삭제된 폴더 ID", example = "1") Long folderId,
        @Schema(description = "처리 결과 메시지", example = "폴더와 내부 즐겨찾기가 모두 삭제되었습니다.") String message
) {
}
