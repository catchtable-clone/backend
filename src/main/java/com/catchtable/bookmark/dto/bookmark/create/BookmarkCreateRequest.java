package com.catchtable.bookmark.dto.bookmark.create;

import jakarta.validation.constraints.NotNull;

public record BookmarkCreateRequest(
        @NotNull Long storeId
) {
}
