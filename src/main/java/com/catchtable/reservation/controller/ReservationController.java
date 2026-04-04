package com.catchtable.reservation.controller;

import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.service.ReservationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationCreateResponseDto> create(
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        ReservationCreateResponseDto response = reservationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationListResponseDto>> getMyReservations(
            @RequestParam Long userId
    ) {
        List<ReservationListResponseDto> response = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(response);
    }

    // 예약 상세 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationDetailResponseDto> getReservationDetail(
            @PathVariable Long reservationId,
            @RequestParam Long userId
    ) {
        ReservationDetailResponseDto response = reservationService.getReservationDetail(reservationId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancel(
            @RequestParam Long userId,
            @PathVariable Long reservationId
    ) {
        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.noContent().build();
    }
}