package com.catchtable.reservation.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.common.SuccessCode;
import com.catchtable.global.security.CustomUserDetails;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import com.catchtable.reservation.dto.update.ReservationUpdateRequestDto;
import com.catchtable.reservation.dto.update.ReservationUpdateResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.catchtable.reservation.service.ReservationService;

import java.util.List;

@Tag(name = "예약", description = "예약 생성 / 조회 / 변경 / 취소 API")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(
            summary = "예약 생성 (결제 준비)",
            description = """
                    예약을 생성하고 결제에 필요한 `orderId`와 `amount`를 반환합니다.

                    **결제 플로우에서 반드시 첫 번째로 호출하세요.**
                    반환된 `orderId`를 PortOne SDK `requestPayment()`의 `paymentId` 파라미터에 그대로 사용하고,
                    결제 완료 후 `POST /payments/confirm`을 호출해야 예약이 확정됩니다.

                    실패 응답 코드: `REMAIN_EXHAUSTED` / `OPTIMISTIC_LOCK_CONFLICT`
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationCreateResponseDto>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        ReservationCreateResponseDto responseData = reservationService.create(userDetails.getUserId(), request);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_CREATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_CREATE_SUCCESS, responseData));
    }

    @Operation(
            summary = "내 예약 목록 조회",
            description = """
                    로그인한 사용자의 예약 목록을 반환합니다.

                    결제 실패로 무산된 예약(`PAYMENT_FAILED`)은 목록에서 제외됩니다.
                    상태 값: `pending` / `confirmed` / `canceled` / `visited` / `noshow` / `replaced`
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReservationListResponseDto>>> getMyReservations(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ReservationListResponseDto> responseData = reservationService.getUserReservations(userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.RESERVATION_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_LOOKUP_SUCCESS, responseData));
    }

    @Operation(
            summary = "예약 상세 조회",
            description = """
                    예약 ID로 특정 예약의 상세 정보를 조회합니다.
                    본인 예약만 조회 가능합니다.

                    실패 응답 코드: `RESERVATION_NOT_FOUND` / `NOT_RESERVATION_OWNER`
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponseDto>> getReservationDetail(
            @Parameter(description = "예약 ID", example = "42") @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ReservationDetailResponseDto responseData = reservationService.getReservationDetail(reservationId, userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.RESERVATION_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_LOOKUP_SUCCESS, responseData));
    }

    @Operation(
            summary = "예약 취소 (결제 환불 포함)",
            description = """
                    예약을 취소합니다. 예약 상태에 따라 처리 방식이 다릅니다.

                    - **결제 완료(`confirmed`) 취소**: PortOne을 통해 카카오페이 자동 환불 → 상태 `canceled`
                    - **결제 미완료(`pending`) 취소**: 환불 없음 → 상태 `payment_failed` (예약 내역 미표시)

                    실패 응답 코드: `ALREADY_CANCELED` / `PAYMENT_REFUND_FAILED`
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @Parameter(description = "취소할 예약 ID", example = "42") @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        reservationService.cancelReservation(reservationId, userDetails.getUserId());
        return ResponseEntity
                .status(SuccessCode.RESERVATION_CANCEL_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_CANCEL_SUCCESS));
    }

    @Operation(
            summary = "예약 변경",
            description = """
                    기존 예약의 날짜/시간/인원을 변경합니다.

                    - 추가 결제 없이 변경됩니다 (기존 결제가 새 예약으로 자동 이전).
                    - 변경된 새 예약은 즉시 `confirmed` 상태로 생성됩니다.
                    - `confirmed` 상태 예약만 변경 가능합니다.

                    실패 응답 코드: `REMAIN_EXHAUSTED` / `OPTIMISTIC_LOCK_CONFLICT` / `ALREADY_CANCELED`
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PatchMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationUpdateResponseDto>> updateReservation(
            @Parameter(description = "변경할 예약 ID", example = "42") @PathVariable Long reservationId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ReservationUpdateRequestDto request
    ) {
        ReservationUpdateResponseDto responseData = reservationService.updateReservation(reservationId, userDetails.getUserId(), request);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_UPDATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_UPDATE_SUCCESS, responseData));
    }
}
