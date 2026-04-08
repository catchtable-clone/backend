package com.catchtable.reservation.service;

import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.update.ReservationUpdateRequestDto;
import com.catchtable.reservation.dto.update.ReservationUpdateResponseDto;
import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.store.entity.Store;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;

    @Transactional
    public ReservationCreateResponseDto create(ReservationCreateRequestDto request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 잔여 재고 조회
        StoreRemain storeRemain = storeRemainRepository.findById(request.remainId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 시간대입니다."));

        // 1. 재고 차감 (이 안에서 remainTeam <= 0 인지 검증하고 차감함)
        storeRemain.decreaseRemainTeam();

        // 2. 예약 생성
        Reservation reservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .member(request.member())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationCreateResponseDto(saved.getReservationId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public List<ReservationListResponseDto> getUserReservations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        List<Reservation> reservations = reservationRepository.findAllByUser(user);

        return reservations.stream().map(reservation -> {
            // Lazy Loading을 통해 DB에서 실제 데이터를 가져옵니다. (여기서 N+1 쿼리 발생)
            StoreRemain storeRemain = reservation.getStoreRemain();
            Store store = storeRemain.getStore();

            return new ReservationListResponseDto(
                    reservation.getReservationId(),
                    storeRemain.getId(),
                    reservation.getStatus().name().toLowerCase(),
                    store.getStoreName(),
                    store.getStoreImage() != null ? store.getStoreImage() : "",
                    storeRemain.getRemainDate(),
                    storeRemain.getRemainTime(),
                    reservation.getMember(),
                    reservation.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponseDto getReservationDetail(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));
        
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예약만 조회할 수 있습니다.");
        }

        // Lazy Loading으로 실제 데이터를 가져옵니다. (여기서 N+1 쿼리 발생 가능성)
        StoreRemain storeRemain = reservation.getStoreRemain();
        Store store = storeRemain.getStore();

        ReservationDetailResponseDto.StoreInfo storeInfo = new ReservationDetailResponseDto.StoreInfo(
                store.getId(),
                store.getStoreName(),
                store.getStoreImage() != null ? store.getStoreImage() : "",
                store.getAddress()
        );

        ReservationDetailResponseDto.RemainInfo remainInfo = new ReservationDetailResponseDto.RemainInfo(
                storeRemain.getId(),
                storeRemain.getRemainDate(),
                storeRemain.getRemainTime()
        );

        return new ReservationDetailResponseDto(
                reservation.getReservationId(),
                reservation.getStatus().name().toLowerCase(),
                reservation.getMember(),
                storeInfo,
                remainInfo,
                reservation.getCreatedAt()
        );
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예약만 취소할 수 있습니다.");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new IllegalArgumentException("이미 취소된 예약입니다.");
        }

        // 1. 예약 상태 취소로 변경
        reservation.changeStatus(ReservationStatus.CANCELED);

        // 2. 예약했던 시간대의 재고 복구 (+1)
        StoreRemain storeRemain = reservation.getStoreRemain();
        storeRemain.increaseRemainTeam();
    }

    @Transactional
    public ReservationUpdateResponseDto updateReservation(Long reservationId, Long userId, ReservationUpdateRequestDto request) {

        Reservation oldReservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다."));

        if (!oldReservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예약만 변경할 수 있습니다.");
        }

        if (oldReservation.getStatus() == ReservationStatus.CANCELED) {
            throw new IllegalArgumentException("이미 취소된 예약은 변경할 수 없습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
                
        StoreRemain newStoreRemain = storeRemainRepository.findById(request.newRemainId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 시간대입니다."));

        // 1. 기존 예약 취소 및 재고 복구 (+1)
        oldReservation.changeStatus(ReservationStatus.CANCELED);
        oldReservation.getStoreRemain().increaseRemainTeam();

        // 2. 새로운 예약 시간에 대한 재고 차감 (-1)
        newStoreRemain.decreaseRemainTeam();

        // 3. 새로운 예약 생성
        Reservation newReservation = Reservation.builder()
                .user(user)
                .storeRemain(newStoreRemain)
                .member(request.newMember())
                .status(ReservationStatus.CONFIRMED) // CONFIRMED 혹은 PENDING
                .build();

        Reservation savedReservation = reservationRepository.save(newReservation);

        return new ReservationUpdateResponseDto(
                savedReservation.getReservationId(),
                savedReservation.getStoreRemain().getId(),
                savedReservation.getMember(),
                savedReservation.getStatus().name().toLowerCase(),
                savedReservation.getUpdatedAt() != null ? savedReservation.getUpdatedAt() : java.time.LocalDateTime.now()
        );
    }
}