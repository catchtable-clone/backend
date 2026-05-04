package com.catchtable.coupon.controller;

import com.catchtable.coupon.dto.create.CouponTemplateCreateRequest;
import com.catchtable.coupon.dto.create.CouponTemplateCreateResponse;
import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.dto.read.CouponReadResponse;
import com.catchtable.coupon.dto.read.CouponTemplateActiveResponse;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<CouponTemplateCreateResponse>> createTemplate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CouponTemplateCreateRequest request) {
        CouponTemplateCreateResponse response = couponService.createTemplate(userDetails.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(SuccessCode.COUPON_TEMPLATE_CREATED, response));
    }

    @PostMapping("/{templateId}/issue")
    public ResponseEntity<ApiResponse<CouponIssueResponse>> issueCoupon(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long templateId) {
        CouponIssueResponse response = couponService.issueCoupon(templateId, userDetails.getUserId());
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.COUPON_ISSUED, response));
    }

    @GetMapping("/templates/active")
    public ResponseEntity<ApiResponse<List<CouponTemplateActiveResponse>>> getActiveTemplates() {
        List<CouponTemplateActiveResponse> response = couponService.getActiveTemplates();
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.COUPON_LIST_OK, response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<CouponReadResponse>>> getMyCoupons(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<CouponReadResponse> response = couponService.getMyCoupons(userDetails.getUserId());
        return ResponseEntity
                .ok(ApiResponse.success(SuccessCode.COUPON_LIST_OK, response));
    }
}
