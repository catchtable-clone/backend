package com.catchtable.notification.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.notification.dto.read.NotificationListResponse;
import com.catchtable.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationListResponse>>> getMyNotifications(
            @RequestParam Long userId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<NotificationListResponse> response = notificationService.getMyNotifications(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_LOOKUP_SUCCESS, response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@RequestParam Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_UNREAD_COUNT_SUCCESS, count));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @RequestParam Long userId) {

        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ_SUCCESS, null));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long notificationId,
            @RequestParam Long userId) {

        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_DELETE_SUCCESS, null));
    }
}
