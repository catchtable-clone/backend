package com.catchtable.reservation.service;

import com.catchtable.coupon.entity.Coupon;
import com.catchtable.coupon.service.CouponService;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.event.ReservationCanceledEvent;
import com.catchtable.notification.event.ReservationChangedEvent;
import com.catchtable.notification.event.ReservationConfirmedEvent;
import com.catchtable.notification.event.ReservationVisitedEvent;
import com.catchtable.notification.event.VacancyEvent;
import com.catchtable.notification.service.VacancyNotificationEmailService;
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

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final CouponService couponService;
    private final VacancyNotificationEmailService vacancyNotificationService;
    private final ApplicationEventPublisher eventPublisher;

    // ============================================================
    // Public API
    // ============================================================

    @Transactional
    public ReservationCreateResponseDto create(Long userId, ReservationCreateRequestDto request) {
        Reservation saved = createReservationCore(userId, request.remainId(), request.member(), request.couponId());
        StoreRemain storeRemain = saved.getStoreRemain();

        // 예약 확정 알림 이벤트 발행
        eventPublisher.publishEvent(new ReservationConfirmedEvent(
                saved.getId(),
                userId,
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate().toString(),
                storeRemain.getRemainTime().toString()
        ));

        return new ReservationCreateResponseDto(saved.getId(), saved.getStatus());
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = cancelReservationCore(reservationId, userId, ReservationStatus.CANCELED);
        StoreRemain storeRemain = reservation.getStoreRemain();

        // 예약 취소 알림 이벤트 발행
        eventPublisher.publishEvent(new ReservationCanceledEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate().toString(),
                storeRemain.getRemainTime().toString()
        ));
    }

    @Transactional
    public ReservationUpdateResponseDto updateReservation(Long reservationId, Long userId, ReservationUpdateRequestDto request) {
        // 1. 기존 예약은 변경으로 대체됨 (REPLACED 마킹) — 취소/생성 로직 재사용, 알림은 발행하지 않음
        Reservation oldReservation = cancelReservationCore(reservationId, userId, ReservationStatus.REPLACED);
        StoreRemain oldStoreRemain = oldReservation.getStoreRemain();

        // 2. 새 예약 생성 — 동일하게 알림은 발행하지 않음
        Reservation newReservation = createReservationCore(userId, request.newRemainId(), request.newMember(), request.couponId());
        StoreRemain newStoreRemain = newReservation.getStoreRemain();

        // 3. 변경 알림 1건만 발행
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

        return reservations.stream().map(reservation -> {
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

        // 상태 변경
        reservation.changeStatus(request.status());

        // 예약 상태가 VISITED로 변경될 때 이벤트 발행
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

    /**
     * 사용자가 직접 "방문 확정" 버튼을 눌러 예약을 VISITED 상태로 전환한다.
     * CONFIRMED 상태에서만 호출 가능. 호출 후 ReservationVisitedEvent 발행으로 알림이 자동 발송된다.
     */
    @Transactional
    public void markAsVisited(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findByIdWithUserAndStoreRemainAndStore(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.validateOwner(userId);

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }

        reservation.changeStatus(ReservationStatus.VISITED);

        StoreRemain storeRemain = reservation.getStoreRemain();
        eventPublisher.publishEvent(new ReservationVisitedEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate().toString(),
                storeRemain.getRemainTime().toString()
        ));
    }

    // ============================================================
    // Internal core (알림 발행 X — 호출자가 알림 정책 결정)
    // ============================================================

    /**
     * 예약 생성의 핵심 로직: 사용자/시간대 검증, 재고 차감, 쿠폰 적용, 저장.
     * 알림은 발행하지 않으므로, 호출자가 상황(생성/변경)에 맞는 이벤트를 직접 publish 해야 한다.
     */
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
                .status(ReservationStatus.CONFIRMED)
                .build();

        return reservationRepository.save(reservation);
    }

    /**
     * 예약 종료(취소/대체) 핵심 로직: 상태 마킹, 재고 복구, 빈자리 이벤트, 쿠폰 반환.
     * 알림은 발행하지 않으므로, 호출자가 상황(취소/변경)에 맞는 이벤트를 직접 publish 해야 한다.
     *
     * @param targetStatus CANCELED(진짜 취소) 또는 REPLACED(변경에 의한 대체)
     */
    private Reservation cancelReservationCore(Long reservationId, Long userId, ReservationStatus targetStatus) {
        Reservation reservation = getActiveReservation(reservationId, userId);
        reservation.changeStatus(targetStatus);

        StoreRemain storeRemain = reservation.getStoreRemain();
        try {
            storeRemain.increaseRemainTeam();
            storeRemainRepository.saveAndFlush(storeRemain);
        } catch (OptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }

        // 빈자리 발생 이벤트는 취소/변경 양쪽 모두에서 발행 (다른 사용자에게 빈자리 알림)
        eventPublisher.publishEvent(new VacancyEvent(storeRemain.getId()));

        if (reservation.getCoupon() != null) {
            couponService.returnCoupon(reservation.getCoupon().getId());
        }

        return reservation;
    }

    private Reservation getActiveReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
        reservation.validateOwner(userId);
        // PENDING/CONFIRMED 상태만 변경/취소 허용. CANCELED/REPLACED/VISITED/NOSHOW 는 모두 종착 상태.
        ReservationStatus status = reservation.getStatus();
        if (status != ReservationStatus.PENDING && status != ReservationStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }
        return reservation;
    }
}
