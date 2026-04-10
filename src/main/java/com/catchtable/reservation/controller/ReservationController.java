package com.catchtable.reservation.controller;

import com.catchtable.global.common.ApiResponse;
import com.catchtable.global.exception.SuccessCode;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import com.catchtable.reservation.dto.update.ReservationUpdateRequestDto;
import com.catchtable.reservation.dto.update.ReservationUpdateResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.catchtable.reservation.service.ReservationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationCreateResponseDto>> create(
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        ReservationCreateResponseDto responseData = reservationService.create(request);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_CREATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_CREATE_SUCCESS, responseData));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReservationListResponseDto>>> getMyReservations(
            @RequestParam Long userId
    ) {
        List<ReservationListResponseDto> responseData = reservationService.getUserReservations(userId);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_LOOKUP_SUCCESS, responseData));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponseDto>> getReservationDetail(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {
        ReservationDetailResponseDto responseData = reservationService.getReservationDetail(reservationId, userId);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_LOOKUP_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_LOOKUP_SUCCESS, responseData));
    }

    @DeleteMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {
        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_CANCEL_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_CANCEL_SUCCESS));
    }

    @PatchMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationUpdateResponseDto>> updateReservation(
            @PathVariable Long reservationId,
            @RequestParam Long userId,
            @Valid @RequestBody ReservationUpdateRequestDto request
    ) {
        ReservationUpdateResponseDto responseData = reservationService.updateReservation(reservationId, userId, request);
        return ResponseEntity
                .status(SuccessCode.RESERVATION_UPDATE_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.RESERVATION_UPDATE_SUCCESS, responseData));
    }
}
