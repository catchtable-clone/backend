package com.catchtable.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "결제 확인 요청")
public record PaymentConfirmRequest(
        @Schema(description = "결제 주문 번호 — POST /reservations 응답의 orderId 값", example = "CATCH-42-1746806400000")
        @NotBlank String paymentId
) {}
