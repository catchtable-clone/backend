package com.catchtable.notification.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.notification.dto.read.NotificationListResponse;
import com.catchtable.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            @RequestHeader("X-User-Id") Long userId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationListResponse> response = notificationService.getMyNotifications(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_LOOKUP_SUCCESS, response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@RequestHeader("X-User-Id") Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_UNREAD_COUNT_SUCCESS, count));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @RequestHeader("X-User-Id") Long userId) {

        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ_SUCCESS, null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_READ_ALL_SUCCESS, null));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long notificationId,
            @RequestHeader("X-User-Id") Long userId) {

        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(SuccessCode.NOTIFICATION_DELETE_SUCCESS, null));
    }
}
