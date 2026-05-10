package com.catchtable.reservation.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.event.ReservationCanceledEvent;
import com.catchtable.notification.event.ReservationChangedEvent;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.payment.entity.Payment;
import com.catchtable.payment.repository.PaymentRepository;
import com.catchtable.payment.service.PaymentService;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.remain.repository.StoreRemainRepository;
import com.catchtable.reservation.dto.create.ReservationCreateRequestDto;
import com.catchtable.reservation.dto.create.ReservationCreateResponseDto;
import com.catchtable.reservation.dto.update.ReservationStatusUpdateRequestDto;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int DEPOSIT_AMOUNT = 10_000;

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    // ============================================================
    // Public API
    // ============================================================

    @Transactional
    public ReservationCreateResponseDto create(Long userId, ReservationCreateRequestDto request) {
        Reservation saved = createReservationCore(userId, request.remainId(), request.member(), request.couponId());

        String orderId = "CATCH-" + saved.getId() + "-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .reservation(saved)
                .orderId(orderId)
                .amount(DEPOSIT_AMOUNT)
                .build();
        paymentRepository.save(payment);

        return new ReservationCreateResponseDto(saved.getId(), orderId, DEPOSIT_AMOUNT, saved.getStatus());
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = getActiveReservation(reservationId, userId);
        StoreRemain storeRemain = reservation.getStoreRemain();

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            // 결제 미완료: PAYMENT_FAILED로 기록 (사용자 예약 취소 내역과 구분)
            paymentRepository.findByReservation_Id(reservationId).ifPresent(Payment::markFailed);
            restoreInventory(reservation);
            reservation.changeStatus(ReservationStatus.PAYMENT_FAILED);
        } else {
            // 결제 완료(CONFIRMED): PortOne 환불 후 CANCELED로 변경
            paymentService.refundPayment(reservation);
            restoreInventory(reservation);
            reservation.changeStatus(ReservationStatus.CANCELED);
            eventPublisher.publishEvent(new ReservationCanceledEvent(
                    reservation.getId(),
                    userId,
                    storeRemain.getStore().getStoreName(),
                    storeRemain.getRemainDate().toString(),
                    storeRemain.getRemainTime().toString()
            ));
        }
    }

    @Transactional
    public ReservationUpdateResponseDto updateReservation(Long reservationId, Long userId, ReservationUpdateRequestDto request) {
        Reservation oldReservation = cancelReservationCore(reservationId, userId, ReservationStatus.REPLACED);
        StoreRemain oldStoreRemain = oldReservation.getStoreRemain();

        // 기존 결제를 새 예약으로 이전
        Payment oldPayment = paymentRepository.findByReservation_Id(reservationId).orElse(null);

        Reservation newReservation = createReservationCore(userId, request.newRemainId(), request.newMember(), request.couponId());
        newReservation.changeStatus(ReservationStatus.CONFIRMED);

        if (oldPayment != null) {
            oldPayment.transferToReservation(newReservation);
        }

        StoreRemain newStoreRemain = newReservation.getStoreRemain();
        eventPublisher.publishEvent(new ReservationChangedEvent(
                newReservation.getId(),
                userId,
                newStoreRemain.getStore().getStoreName(),
                oldStoreRemain.getRemainDate().toString(),
                oldStoreRemain.getRemainTime().toString(),
                newStoreRemain.getRemainDate().toString(),
                newStoreRemain.getRemainTime().toString()
        ));

        return new ReservationUpdateResponseDto(
                newReservation.getId(),
                newReservation.getStoreRemain().getId(),
                newReservation.getMember(),
                newReservation.getStatus().name().toLowerCase(),
                newReservation.getUpdatedAt() != null ? newReservation.getUpdatedAt() : java.time.LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationListResponseDto> getUserReservations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Reservation> reservations = reservationRepository.findAllByUser(user);

        return reservations.stream()
                .filter(r -> r.getStatus() != ReservationStatus.PAYMENT_FAILED)
                .map(reservation -> {
            StoreRemain storeRemain = reservation.getStoreRemain();
            Store store = storeRemain.getStore();

            return new ReservationListResponseDto(
                    reservation.getId(),
                    storeRemain.getId(),
                    reservation.getStatus().name().toLowerCase(),
                    store.getId(),
                    store.getStoreName(),
                    store.getStoreImage() != null ? store.getStoreImage() : "",
                    store.getCategory().name(),
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
                store.getCategory().name(),
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
    public void updateReservationStatus(Long reservationId, Long userId, ReservationStatusUpdateRequestDto request) {
        Reservation reservation = reservationRepository.findByIdWithUserAndStoreRemainAndStore(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.validateOwner(userId);
        reservation.changeStatus(request.status());

        if (request.status() == ReservationStatus.VISITED) {
            eventPublisher.publishEvent(new ReservationVisitedEvent(
                    reservation.getId(),
                    reservation.getUser().getId(),
                    reservation.getStoreRemain().getStore().getStoreName(),
                    reservation.getStoreRemain().getRemainDate().toString(),
                    reservation.getStoreRemain().getRemainTime().toString()
            ));
        }
    }

    // ============================================================
    // Internal core
    // ============================================================

    private Reservation createReservationCore(Long userId, Long remainId, Integer member, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StoreRemain storeRemain = storeRemainRepository.findByIdWithStore(remainId)
                .orElseThrow(() -> new CustomException(ErrorCode.REMAIN_NOT_FOUND));

        try {
            storeRemain.decreaseRemainTeam();
            storeRemainRepository.saveAndFlush(storeRemain);
        } catch (OptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }

        Coupon coupon = null;
        if (couponId != null) {
            coupon = couponService.useCoupon(couponId, userId);
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .storeRemain(storeRemain)
                .coupon(coupon)
                .member(member)
                .status(ReservationStatus.PENDING)
                .build();

        return reservationRepository.save(reservation);
    }

    private Reservation cancelReservationCore(Long reservationId, Long userId, ReservationStatus targetStatus) {
        Reservation reservation = getActiveReservation(reservationId, userId);
        reservation.changeStatus(targetStatus);
        restoreInventory(reservation);
        return reservation;
    }

    private void restoreInventory(Reservation reservation) {
        StoreRemain storeRemain = reservation.getStoreRemain();
        try {
            storeRemain.increaseRemainTeam();
            storeRemainRepository.saveAndFlush(storeRemain);
        } catch (OptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        eventPublisher.publishEvent(new VacancyEvent(storeRemain.getId()));
        if (reservation.getCoupon() != null) {
            couponService.returnCoupon(reservation.getCoupon().getId());
        }
    }

    private Reservation getActiveReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        reservation.validateOwner(userId);
        ReservationStatus status = reservation.getStatus();
        if (status != ReservationStatus.PENDING && status != ReservationStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }
        return reservation;
    }
}
