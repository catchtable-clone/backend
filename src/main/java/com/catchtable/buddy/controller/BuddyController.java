package com.catchtable.buddy.controller;

import com.catchtable.buddy.dto.request.BuddySendRequest;
import com.catchtable.buddy.dto.response.BuddyListResponse;
import com.catchtable.buddy.dto.response.BuddyPendingRequestResponse;
import com.catchtable.buddy.dto.response.BuddyRequestResponse;
import com.catchtable.buddy.service.BuddyService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Buddy", description = "Buddy (friend) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class BuddyController {

    private final BuddyService buddyService;

    @Operation(summary = "버디 요청 전송", description = "다른 사용자에게 버디 요청을 보냅니다.")
    @PostMapping("/buddies/requests")
    public ResponseEntity<ApiResponse<BuddyRequestResponse>> sendRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BuddySendRequest request) {
        BuddyRequestResponse response = buddyService.sendRequest(userDetails.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.BUDDY_REQUEST_SENT, response));
    }

    @Operation(summary = "받은 버디 요청 목록 조회", description = "나에게 온 대기 중인 버디 요청 목록을 조회합니다.")
    @GetMapping("/buddies/requests")
    public ResponseEntity<ApiResponse<List<BuddyPendingRequestResponse>>> getPendingRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<BuddyPendingRequestResponse> response = buddyService.getPendingRequests(userDetails.getUserId());
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BUDDY_REQUEST_LIST_OK, response));
    }

    @Operation(summary = "버디 요청 수락", description = "받은 버디 요청을 수락합니다.")
    @PostMapping("/buddies/requests/{requestId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "요청 ID", example = "1") @PathVariable Long requestId) {
        buddyService.acceptRequest(userDetails.getUserId(), requestId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BUDDY_REQUEST_ACCEPTED));
    }

    @Operation(summary = "버디 요청 거절", description = "받은 버디 요청을 거절합니다.")
    @PostMapping("/buddies/requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "요청 ID", example = "1") @PathVariable Long requestId) {
        buddyService.rejectRequest(userDetails.getUserId(), requestId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BUDDY_REQUEST_REJECTED));
    }

    @Operation(summary = "버디 목록 조회", description = "나의 버디 목록을 조회합니다.")
    @GetMapping("/buddies")
    public ResponseEntity<ApiResponse<List<BuddyListResponse>>> getBuddies(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<BuddyListResponse> response = buddyService.getBuddies(userDetails.getUserId());
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BUDDY_LIST_OK, response));
    }

    @Operation(summary = "버디 삭제", description = "버디 관계를 삭제합니다.")
    @DeleteMapping("/buddies/{buddyId}")
    public ResponseEntity<ApiResponse<Void>> deleteBuddy(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "버디 ID", example = "1") @PathVariable Long buddyId) {
        buddyService.deleteBuddy(userDetails.getUserId(), buddyId);
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.BUDDY_DELETED));
    }
}
