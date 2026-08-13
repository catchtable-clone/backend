package com.catchtable.buddy.dto.response;

import java.time.LocalDateTime;

public record BuddyPendingRequestResponse(
        Long requestId,
        Long requesterId,
        String requesterNickname,
        String requesterProfileImage,
        LocalDateTime requestedAt
) {
}
