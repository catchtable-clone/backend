package com.catchtable.reservation.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.repository.CouponRepository;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.service.VacancyNotificationService;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.update.ReservationUpdateRequestDto;
import com.catchtable.reservation.dto.update.ReservationUpdateResponseDto;
import com.catchtable.reservation.dto.read.ReservationDetailResponseDto;
import com.catchtable.reservation.dto.read.ReservationListResponseDto;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import com.catchtable.reservation.repository.ReservationRepository;
import com.catchtable.store.entity.Store;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final VacancyNotificationService vacancyNotificationService;

    @Transactional
    public ReservationCreateResponseDto create(ReservationCreateRequestDto request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StoreRemain storeRemain = storeRemainRepository.findByIdWithStore(request.remainId())
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        // 재고 차감 (remainTeam <= 0 검증 포함)
        storeRemain.decreaseRemainTeam();

        // 쿠폰 적용 (선택)
        Coupon coupon = null;
        if (request.couponId() != null) {
            couponService.useCoupon(request.couponId(), request.userId());
            coupon = couponRepository.findById(request.couponId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .coupon(coupon)
                .member(request.member())
                .build();

        Reservation saved = reservationRepository.save(reservation);
        return new ReservationCreateResponseDto(saved.getId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public List<ReservationListResponseDto> getUserReservations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Reservation> reservations = reservationRepository.findAllByUser(user);

        return reservations.stream().map(reservation -> {
            StoreRemain storeRemain = reservation.getStoreRemain();
            Store store = storeRemain.getStore();

            return new ReservationListResponseDto(
                    reservation.getId(),
                    storeRemain.getId(),
                    reservation.getStatus().name().toLowerCase(),
                    store.getStoreName(),
                    store.getStoreImage() != null ? store.getStoreImage() : "",
                    storeRemain.getRemainDate(),
                    storeRemain.getRemainTime(),
                    reservation.getMember(),
                    reservation.getCreatedAt()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponseDto getReservationDetail(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        
        reservation.validateOwner(userId);

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
                reservation.getId(),
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
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.validateOwner(userId);

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }

        // 예약 상태 취소로 변경
        reservation.changeStatus(ReservationStatus.CANCELED);

        // 예약했던 시간대의 재고 복구
        StoreRemain storeRemain = reservation.getStoreRemain();
        storeRemain.increaseRemainTeam();

        // 빈자리 알림 구독자에게 알림 발송
        vacancyNotificationService.notifySubscribers(storeRemain.getId());

        // 쿠폰 반환
        if (reservation.getCoupon() != null) {
            couponService.returnCoupon(reservation.getCoupon().getId());
        }
    }

    @Transactional
    public ReservationUpdateResponseDto updateReservation(Long reservationId, Long userId, ReservationUpdateRequestDto request) {
        Reservation oldReservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        oldReservation.validateOwner(userId);

        if (oldReservation.getStatus() == ReservationStatus.CANCELED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }

        StoreRemain newStoreRemain = storeRemainRepository.findByIdWithStore(request.newRemainId())
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        // 기존 예약 취소 및 재고 복구
        oldReservation.changeStatus(ReservationStatus.CANCELED);
        oldReservation.getStoreRemain().increaseRemainTeam();

        // 기존 쿠폰 반환
        if (oldReservation.getCoupon() != null) {
            couponService.returnCoupon(oldReservation.getCoupon().getId());
        }

        // 새로운 예약 시간에 대한 재고 차감
        newStoreRemain.decreaseRemainTeam();

        // 새 예약에 쿠폰 적용 (선택)
        Coupon newCoupon = null;
        if (request.couponId() != null) {
            couponService.useCoupon(request.couponId(), oldReservation.getUser().getId());
            newCoupon = couponRepository.findById(request.couponId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COUPON_NOT_FOUND));
        }

        // 새로운 예약 생성
        Reservation newReservation = Reservation.builder()
                .user(oldReservation.getUser()) // 기존 User 재사용
                .storeRemain(newStoreRemain)
                .coupon(newCoupon)
                .member(request.newMember())
                .status(ReservationStatus.PENDING)
                .build();

        Reservation savedReservation = reservationRepository.save(newReservation);

        return new ReservationUpdateResponseDto(
                savedReservation.getId(),
                savedReservation.getStoreRemain().getId(),
                savedReservation.getMember(),
                savedReservation.getStatus().name().toLowerCase(),
                savedReservation.getUpdatedAt() != null ? savedReservation.getUpdatedAt() : java.time.LocalDateTime.now()
        );
    }
}
