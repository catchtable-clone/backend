package com.catchtable.reservation.service;

import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.update.ReservationUpdateRequestDto;
import com.catchtable.reservation.dto.update.ReservationUpdateResponseDto;
import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import com.catchtable.reservation.entity.ReservationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ReservationCreateResponseDto create(ReservationCreateRequestDto request) {
        Reservation reservation = Reservation.builder()
                .userId(request.userId())
                .remainId(request.remainId())
                .member(request.member())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationCreateResponseDto(saved.getReservationId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public List<ReservationListResponseDto> getUserReservations(Long userId) {
        List<Reservation> reservations = reservationRepository.findAllByUserId(userId);

        return reservations.stream().map(reservation -> {

            String mockStoreName = (reservation.getReservationId() % 2 == 0) ? "식당네오" : "모수 서울";
            String mockStoreImage = "https://s3.amazonaws.com/bucket/image.png";
            LocalDate mockDate = LocalDate.of(2026, 1, 1);
            LocalTime mockTime = LocalTime.of(12, 0);

            return new ReservationListResponseDto(
                    reservation.getReservationId(),
                    reservation.getRemainId(),
                    reservation.getStatus().name().toLowerCase(), // "PENDING" -> "pending"
                    mockStoreName,
                    mockStoreImage,
                    mockDate,
                    mockTime,
                    reservation.getMember(),
                    reservation.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponseDto getReservationDetail(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

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
                reservation.getStatus().name().toLowerCase(),
                reservation.getMember(),
                mockStoreInfo,
                mockRemainInfo,
                reservation.getCreatedAt()
        );
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        reservation.changeStatus(ReservationStatus.CANCELED);

        // remain 롤백 로직 필요

        System.out.println("예약 ID " + reservationId + " 취소 및 재고 반납 로직 실행 예정");
    }

    @Transactional
    public ReservationUpdateResponseDto updateReservation(Long reservationId, Long userId, ReservationUpdateRequestDto request) {

        Reservation oldReservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        // 기존 예약 취소
        oldReservation.changeStatus(ReservationStatus.CANCELED);
        // remain 롤백 로직 필요

        // 새로운 예약 생성
        // 새로운 예약에 대한 remain 차감
        Reservation newReservation = Reservation.builder()
                .userId(userId)
                .remainId(request.newRemainId())
                .member(request.newMember())
                .status(ReservationStatus.CONFIRMED) // CONFIRMED 혹은 PENDING
                .build();

        Reservation savedReservation = reservationRepository.save(newReservation);

        return new ReservationUpdateResponseDto(
                savedReservation.getReservationId(),
                savedReservation.getRemainId(),
                savedReservation.getMember(),
                savedReservation.getStatus().name().toLowerCase(),
                savedReservation.getUpdatedAt() != null ? savedReservation.getUpdatedAt() : java.time.LocalDateTime.now()
        );
    }
}
