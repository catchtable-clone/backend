package com.catchtable.coupon.controller;

import com.catchtable.coupon.dto.issue.CouponIssueResponse;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController couponController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // === 쿠폰 템플릿 생성 ===

    @Test
    @DisplayName("쿠폰 템플릿 생성 성공 - 201")
    void createTemplateSuccess() throws Exception {
        given(couponService.createTemplate(eq(1L), any())).willReturn(
                new com.catchtable.coupon.dto.create.CouponTemplateCreateResponse(
                        1L, "10% 할인", 10, 100,
                        "2026-04-10T00:00", "2026-05-10T23:59:59"));

        String requestBody = """
                {
                    "couponName": "10% 할인",
                    "discountRate": 10,
                    "amount": 100,
                    "startedAt": "2026-04-10T00:00:00",
                    "expiredAt": "2026-05-10T23:59:59"
                }
                """;

        mockMvc.perform(post("/api/v1/coupons/templates")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201));
    }

    @Test
    @DisplayName("쿠폰 템플릿 생성 실패 - 입력값 검증 400")
    void createTemplateValidationFail() throws Exception {
        String requestBody = """
                {
                    "discountRate": 10
                }
                """;

        mockMvc.perform(post("/api/v1/coupons/templates")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("쿠폰 템플릿 생성 실패 - 권한 없음 403")
    void createTemplateAccessDenied() throws Exception {
        given(couponService.createTemplate(eq(2L), any()))
                .willThrow(new CustomException(ErrorCode.ADMIN_ONLY_COUPON_CREATE));

        String requestBody = """
                {
                    "couponName": "10% 할인",
                    "discountRate": 10,
                    "amount": 100,
                    "startedAt": "2026-04-10T00:00:00",
                    "expiredAt": "2026-05-10T23:59:59"
                }
                """;

        mockMvc.perform(post("/api/v1/coupons/templates")
                        .header("X-User-Id", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // === 쿠폰 발급 ===

    @Test
    @DisplayName("쿠폰 발급 실패 - 중복 발급 400")
    void issueCouponDuplicate() throws Exception {
        given(couponService.issueCoupon(eq(1L), eq(1L)))
                .willThrow(new CustomException(ErrorCode.DUPLICATE_COUPON));

        mockMvc.perform(post("/api/v1/coupons/1/issue")
                        .header("X-User-Id", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("쿠폰 발급 실패 - 템플릿 없음 404")
    void issueCouponTemplateNotFound() throws Exception {
        given(couponService.issueCoupon(eq(999L), eq(1L)))
                .willThrow(new CustomException(ErrorCode.COUPON_TEMPLATE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/coupons/999/issue")
                        .header("X-User-Id", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
