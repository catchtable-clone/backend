package com.catchtable.bookmark.dto.bookmark.read;

import com.catchtable.store.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 목록 조회 응답")
public record BookmarkListResponse(
        @Schema(description = "북마크 ID", example = "1") Long bookmarkId,
        @Schema(description = "매장 ID", example = "1") Long storeId,
        @Schema(description = "매장 이름", example = "진진") String storeName,
        @Schema(description = "매장 이미지", example = "jinjin.jpg") String storeImage,
        @Schema(description = "카테고리") Category category,
        @Schema(description = "주소", example = "서울시 강남구") String address
) {
}
