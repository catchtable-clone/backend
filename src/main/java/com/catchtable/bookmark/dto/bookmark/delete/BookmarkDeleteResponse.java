package com.catchtable.bookmark.dto.bookmark.delete;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 삭제 응답")
public record BookmarkDeleteResponse(
        @Schema(description = "삭제된 북마크 ID", example = "1") Long bookmarkId,
        @Schema(description = "처리 결과 메시지", example = "삭제 완료") String message
) {
}
