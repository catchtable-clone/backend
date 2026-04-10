package com.catchtable.coupon.controller;

import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/{templateId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issueCoupon(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long templateId) {
        CouponIssueResponse response = couponService.issueCoupon(templateId, userId);
        return ResponseEntity
                .ok(ApiResponse.success(ResponseCode.COUPON_ISSUED, response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<CouponReadResponse>>> getMyCoupons(
            @RequestHeader("X-User-Id") Long userId) {
        List<CouponReadResponse> response = couponService.getMyCoupons(userId);
        return ResponseEntity
                .ok(ApiResponse.success(ResponseCode.COUPON_LIST_OK, response));
    }
}
