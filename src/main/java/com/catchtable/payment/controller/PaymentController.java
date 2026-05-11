package com.catchtable.payment.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import com.catchtable.payment.dto.PaymentConfirmRequest;
import com.catchtable.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제", description = "결제 확인 및 검증 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 확인 (결제 검증)",
            description = """
                    PortOne(카카오페이) 결제 완료 후 백엔드에서 결제를 검증하고 예약을 확정합니다.

                    **호출 순서 (프론트 필독)**
                    1. `POST /reservations` → 예약 생성 → `orderId`, `amount` 수신
                    2. PortOne SDK `requestPayment()` 호출 → 카카오페이 결제창 오픈
                    3. 결제 성공 시 (`paymentResult.code == null`) → 이 API 호출
                    4. 결제 취소/실패 시 (`paymentResult.code != null`) → `DELETE /reservations/{id}` 호출

                    **검증 내용**
                    - PortOne API로 실제 결제 여부 확인
                    - 결제 금액 일치 여부 확인 (예치금 10,000원)
                    - 호출자가 예약 본인인지 확인

                    **성공 시**: 예약 상태 `PENDING` → `CONFIRMED`, 예약 확정 이메일 발송
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "결제 검증 성공 — 예약 확정 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "PAYMENT_CONFIRM_SUCCESS",
                                      "message": "결제가 확인되었습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "결제 검증 실패 (PortOne 결제 미완료 또는 금액 불일치)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "PAYMENT_VERIFICATION_FAILED",
                                      "message": "결제 검증에 실패했습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "본인의 예약이 아닌 경우",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "FORBIDDEN",
                                      "message": "접근 권한이 없습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 orderId의 결제 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "PAYMENT_NOT_FOUND",
                                      "message": "존재하지 않는 결제입니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "PortOne API 통신 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "PAYMENT_PORTONE_API_ERROR",
                                      "message": "포트원 API 호출 중 오류가 발생했습니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "결제 확인 요청 — `POST /reservations` 응답의 `orderId` 값을 그대로 사용",
                    content = @Content(
                            schema = @Schema(implementation = PaymentConfirmRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "paymentId": "CATCH-42-1746806400000"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        paymentService.confirmPayment(request.paymentId(), userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.PAYMENT_CONFIRM_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.PAYMENT_CONFIRM_SUCCESS));
    }
}
