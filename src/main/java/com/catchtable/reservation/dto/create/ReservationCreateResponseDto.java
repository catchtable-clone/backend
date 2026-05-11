package com.catchtable.reservation.dto.create;

import com.catchtable.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예약 생성 응답 — PortOne 결제창 호출에 필요한 정보 포함")
public record ReservationCreateResponseDto(
        @Schema(description = "예약 ID", example = "42")
        Long id,

        @Schema(description = "결제 주문 번호 — PortOne SDK의 paymentId 파라미터에 그대로 사용", example = "CATCH-42-1746806400000")
        String orderId,

        @Schema(description = "결제 금액 (예약 예치금, 고정값)", example = "10000")
        Integer amount,

        @Schema(description = "예약 상태 — 결제 완료 전까지 PENDING", example = "PENDING")
        ReservationStatus status
) {
}
