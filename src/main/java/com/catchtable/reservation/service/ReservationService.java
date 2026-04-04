package com.catchtable.reservation.service;

import org.springframework.stereotype.Service;

import com.catchtable.reservation.dto.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.ReservationCreateResponseDto;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationCreateResponseDto create(ReservationCreateRequestDto request) {
        // 상태는 PENDING 으로 고정
        Reservation reservation = Reservation.builder()
                .userId(request.userId())
                .remainId(request.remainId())
                .member(request.member())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationCreateResponseDto(saved.getReservationId(), saved.getStatus());
    }
}

