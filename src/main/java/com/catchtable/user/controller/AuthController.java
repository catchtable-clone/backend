package com.catchtable.user.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.user.dto.auth.GoogleLoginRequest;
import com.catchtable.user.dto.auth.RefreshTokenRequest;
import com.catchtable.user.dto.auth.TokenResponse;
import com.catchtable.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "인증", description = "소셜 로그인 및 토큰 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "구글 소셜 로그인", description = "프론트에서 발급받은 Google ID Token으로 로그인합니다.")
    @PostMapping("/google/login")
    public ResponseEntity<ApiResponse<TokenResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        TokenResponse response = authService.googleLogin(request.idToken());
        log.info("Swagger 테스트용 Access Token: {}", response.accessToken());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새로운 Access Token을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.TOKEN_REFRESHED, response));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.LOGOUT_SUCCESS));
    }
}
