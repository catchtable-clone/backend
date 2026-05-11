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
import com.catchtable.remain.service.StoreRemainService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final StoreRemainService storeRemainService;
    private final CouponService couponService;
    private final VacancyNotificationEmailService vacancyNotificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Tool(description = "사용자의 자연어 요청을 기반으로 레스토랑 예약을 생성합니다. " +
            "매장 이름은 사용자가 말한 그대로 넘겨주세요. 임의로 변경하지 마세요. " +
            "매장 이름, 예약 날짜, 예약 시간, 인원수 정보가 모두 필요합니다. " +
            "사용자가 예약을 요청하면 반드시 이 함수를 호출하세요.")
    @Transactional
    public String createReservationFromAi(
            @ToolParam(description = "매장 이름 (예: 모수 서울, 경원집)") String storeName,
            @ToolParam(description = "예약 날짜, ISO 형식 (예: 2025-05-11)") LocalDate date,
            @ToolParam(description = "예약 시간, HH:mm 형식 (예: 14:00)") LocalTime time,
            @ToolParam(description = "예약 인원수 (예: 2)") int member,
            ToolContext toolContext
    ) {
        Long currentUserId = (Long) toolContext.getContext().get("userId");

        log.info("=== AI Tool 호출 === storeName='{}', date={}, time={}, member={}, userId={}",
                storeName, date, time, member, currentUserId);

        Optional<StoreRemain> availableRemain =
                storeRemainService.findAvailableRemain(storeName, date, time);

        log.info("=== 잔여석 조회 결과: {}",
                availableRemain.isPresent() ? "있음 (id=" + availableRemain.get().getId() + ")" : "없음");

        if (availableRemain.isEmpty()) {
            return "죄송합니다. 요청하신 시간에 예약 가능한 자리가 없습니다.";
        }

        Reservation saved = createReservationCore(
                currentUserId, availableRemain.get().getId(), member, null);

        eventPublisher.publishEvent(new ReservationConfirmedEvent(
                saved.getId(),
                currentUserId,
                saved.getStoreRemain().getStore().getStoreName(),
                saved.getStoreRemain().getRemainDate().toString(),
                saved.getStoreRemain().getRemainTime().toString()
        ));

        return String.format(
                "네, %s 레스토랑 %s %s 시간으로 %d명 예약이 완료되었습니다. 예약 번호는 %d번입니다.",
                storeName, date, time, member, saved.getId());
    }
    
    @Transactional
    public ReservationCreateResponseDto create(Long userId, ReservationCreateRequestDto request) {
        Reservation saved = createReservationCore(userId, request.remainId(), request.member(), request.couponId());
        StoreRemain storeRemain = saved.getStoreRemain();

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
        Reservation oldReservation = cancelReservationCore(reservationId, userId, ReservationStatus.REPLACED);
        StoreRemain oldStoreRemain = oldReservation.getStoreRemain();

        Reservation newReservation = createReservationCore(userId, request.newRemainId(), request.newMember(), request.couponId());
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

    // Basic Logic
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
                .status(ReservationStatus.CONFIRMED)
                .build();

        return reservationRepository.save(reservation);
    }

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
        ReservationStatus status = reservation.getStatus();
        if (status != ReservationStatus.PENDING && status != ReservationStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.ALREADY_CANCELED);
        }
        return reservation;
    }
}
