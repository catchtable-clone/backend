package com.catchtable.reservation.service;

import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import org.springframework.stereotype.Service;

import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

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

    //내 예약 리스트 조회
    public List<ReservationListResponseDto> getUserReservations(Long userId) {
        List<Reservation> reservations = reservationRepository.findAllByUserId(userId);

        return reservations.stream().map(reservation -> {

            //일단 목킹
            String mockStoreName = (reservation.getReservationId() % 2 == 0) ? "식당네오" : "모수 서울";
            String mockStoreImage = "https://s3.amazonaws.com/bucket/image.png";
            LocalDate mockDate = LocalDate.of(2026, 1, 1);
            LocalTime mockTime = LocalTime.of(12, 0);

            return new ReservationListResponseDto(
                    reservation.getReservationId(),
                    reservation.getRemainId(),
                    reservation.getStatus().name(),
                    mockStoreName,
                    mockStoreImage,
                    mockDate,
                    mockTime,
                    reservation.getMember(),
                    reservation.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    public ReservationDetailResponseDto getReservationDetail(Long reservationId, Long userId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        //목킹
        ReservationDetailResponseDto.StoreInfo mockStoreInfo = new ReservationDetailResponseDto.StoreInfo(
                1L,
                "모수 서울",
                "https://s3.amazonaws.com/bucket/image.png",
                "서울특별시 용산구 회나무로41길 4"
        );

        ReservationDetailResponseDto.RemainInfo mockRemainInfo = new ReservationDetailResponseDto.RemainInfo(
                reservation.getRemainId(),
                LocalDate.of(2026, 1, 1),
                LocalTime.of(12, 0)
        );

        return new ReservationDetailResponseDto(
                reservation.getReservationId(),
                reservation.getStatus().name(),
                reservation.getMember(),
                mockStoreInfo,
                mockRemainInfo,
                reservation.getCreatedAt()
        );
    }

}

