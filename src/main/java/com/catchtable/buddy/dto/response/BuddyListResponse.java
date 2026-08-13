package com.catchtable.buddy.dto.response;

public record BuddyListResponse(
        Long buddyId,
        Long userId,
        String nickname,
        String profileImage
) {
}
