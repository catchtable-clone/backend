package com.catchtable.notification.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import com.catchtable.notification.dto.read.NotificationListResponse;
import com.catchtable.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationListResponse>>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationListResponse> response = notificationService.getMyNotifications(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_LOOKUP_SUCCESS, response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = notificationService.getUnreadCount(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_UNREAD_COUNT_SUCCESS, count));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAsRead(notificationId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ_SUCCESS, null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ_ALL_SUCCESS, null));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.deleteNotification(notificationId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_DELETE_SUCCESS, null));
    }
}
