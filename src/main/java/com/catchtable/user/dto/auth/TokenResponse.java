package com.catchtable.user.dto.auth;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String nickname,
        String profileImage
) {}
