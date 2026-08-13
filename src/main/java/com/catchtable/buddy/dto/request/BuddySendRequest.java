package com.catchtable.buddy.dto.request;

import jakarta.validation.constraints.NotNull;

public record BuddySendRequest(
        @NotNull Long receiverId
) {
}
