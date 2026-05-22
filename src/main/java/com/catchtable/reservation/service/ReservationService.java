package com.catchtable.reservation.service;

import com.catchtable.chatbot.dto.create.PendingPaymentHolder;
import com.catchtable.chatbot.dto.create.PendingPaymentInfo;
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
import com.catchtable.store.repository.StoreRepository;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int DEPOSIT_AMOUNT = 10_000;

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StoreRemainRepository storeRemainRepository;
    private final StoreRemainService storeRemainService;
    private final CouponService couponService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final StoreRepository storeRepository;

    // ============================================================
    // AI Tools
    // ============================================================

    @Tool(description = "매장 이름으로 검색합니다. 사용자가 말한 매장명이 정확하지 않을 수 있으므로, " +
            "예약 전에 이 함수로 유사한 매장 이름을 찾아 사용자에게 확인을 받으세요.")
    public List<String> searchStoresByName(
            @ToolParam(description = "검색할 매장 이름 또는 키워드") String name
    ) {
        List<String> results = storeRepository.findNamesByNameContaining(name, PageRequest.of(0, 5));
        if (results.isEmpty()) {
            return List.of("검색 결과가 없습니다. 다른 키워드로 검색해보세요.");
        }
        return results;
    }

    @Tool(description = "사용자의 자연어 요청을 기반으로 레스토랑 예약을 생성합니다. " +
            "반드시 searchStoresByName으로 매장명을 확인한 후 호출하세요. " +
            "매장 이름, 예약 날짜, 예약 시간, 인원수가 모두 필요합니다.")
    @Transactional
    public String createReservationFromAi(
            @ToolParam(description = "매장 이름 (정확한 이름 사용)") String storeName,
            @ToolParam(description = "예약 날짜, ISO 형식 (예: 2025-05-11)") LocalDate date,
            @ToolParam(description = "예약 시간, HH:mm 형식 (예: 14:00)") LocalTime time,
            @ToolParam(description = "예약 인원수 (예: 2)") int member,
            @ToolParam(description = "사용할 쿠폰의 ID (선택 사항, 없으면 null)") Long couponId,
            ToolContext toolContext
    ) {
        Long currentUserId = (Long) toolContext.getContext().get("userId");

        log.info("=== AI Tool 호출: createReservationFromAi ===\nuserId: {},\nstoreName: '{}',\ndate: {},\ntime: {},\nmember: {},\ncouponId: {}",
                currentUserId, storeName, date, time, member, couponId);

        Optional<StoreRemain> availableRemain =
                storeRemainService.findAvailableRemain(storeName, date, time);

        log.info("=== 잔여석 조회 결과: {}",
                availableRemain.isPresent() ? "있음 (id=" + availableRemain.get().getId() + ")" : "없음");

        if (availableRemain.isEmpty()) {
            log.warn("AI 예약 실패: 사용 가능한 재고 없음. storeName='{}', date={}, time={}", storeName, date, time);
            return "STORE_OR_SLOT_NOT_FOUND: 요청하신 매장(" + storeName + ")의 " + date + " " + time + " 시간대에 예약 가능한 자리가 없습니다.";
        }

        Reservation saved = createReservationCore(
                currentUserId, availableRemain.get().getId(), member, couponId);

        // Payment 레코드 생성 (결제창 호출을 위해 orderId 필요)
        String orderId = "CATCH-" + saved.getId() + "-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .reservation(saved)
                .orderId(orderId)
                .amount(DEPOSIT_AMOUNT)
                .build();
        paymentRepository.save(payment);

        log.info("AI 예약 성공: reservationId={}, orderId={}", saved.getId(), orderId);

        PendingPaymentHolder.set(new PendingPaymentInfo(saved.getId(), orderId, DEPOSIT_AMOUNT));

        return String.format(
                "네, %s 레스토랑 %s %s 시간으로 %d명 예약이 완료되었습니다. " +
                "보증금 10,000원 결제 후 예약이 최종 확정됩니다. 예약 번호는 %d번입니다.",
                storeName, date, time, member, saved.getId());
    }

    @Tool(description = "사용자의 예약 목록을 조회합니다. '내 예약 보여줘', '예약 현황' 등의 요청에 사용하세요.")
    @Transactional(readOnly = true)
    public String getMyReservationsForAi(ToolContext toolContext) {
        Long currentUserId = (Long) toolContext.getContext().get("userId");
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Reservation> reservations = reservationRepository.findAllByUserAndStatusIn(
                user, List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED));

        if (reservations.isEmpty()) {
            return "현재 예정된 예약이 없습니다.";
        }

        return reservations.stream().map(r -> {
            StoreRemain sr = r.getStoreRemain();
            return String.format("예약번호 %d: %s %s %s, %d명, 상태: %s",
                    r.getId(),
                    sr.getStore().getStoreName(),
                    sr.getRemainDate(),
                    sr.getRemainTime(),
                    r.getMember(),
                    r.getStatus() == ReservationStatus.PENDING ? "결제 대기" : "확정");
        }).collect(Collectors.joining("\n"));
    }

    @Tool(description = "취소되거나 노쇼 처리된 예약 내역을 조회합니다. '취소된 예약 보여줘', '노쇼 내역', '취소 내역' 등의 요청에 사용하세요.")
    @Transactional(readOnly = true)
    public String getCanceledReservationsForAi(ToolContext toolContext) {
        Long currentUserId = (Long) toolContext.getContext().get("userId");
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Reservation> reservations = reservationRepository.findAllByUserAndStatusIn(
                user, List.of(ReservationStatus.CANCELED, ReservationStatus.NOSHOW));

        if (reservations.isEmpty()) {
            return "취소되거나 노쇼 처리된 예약 내역이 없습니다.";
        }

        return reservations.stream().map(r -> {
            StoreRemain sr = r.getStoreRemain();
            String statusLabel = r.getStatus() == ReservationStatus.CANCELED ? "취소" : "노쇼";
            return String.format("예약번호 %d: %s %s %s, %d명, 상태: %s",
                    r.getId(),
                    sr.getStore().getStoreName(),
                    sr.getRemainDate(),
                    sr.getRemainTime(),
                    r.getMember(),
                    statusLabel);
        }).collect(Collectors.joining("\n"));
    }

    @Tool(description = "예약을 취소합니다. '예약 취소해줘', '예약번호 X 취소' 등의 요청에 사용하세요.")
    @Transactional
    public String cancelReservationFromAi(
            @ToolParam(description = "취소할 예약 번호 (예약 ID)") Long reservationId,
            ToolContext toolContext
    ) {
        Long currentUserId = (Long) toolContext.getContext().get("userId");
        try {
            cancelReservation(reservationId, currentUserId);
            return "예약번호 " + reservationId + "번 예약이 취소되었습니다.";
        } catch (CustomException e) {
            return "예약 취소에 실패했습니다: " + e.getMessage();
        }
    }
    
    @Transactional
    public ReservationCreateResponseDto create(Long userId, ReservationCreateRequestDto request) {
        Reservation saved = createReservationCore(userId, request.remainId(), request.member(), request.couponId());

        // ConfirmedEvent는 결제 완료 시점(PaymentService.confirmPayment)에서 발행한다.
        String orderId = "CATCH-" + saved.getId() + "-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .reservation(saved)
                .orderId(orderId)
                .amount(DEPOSIT_AMOUNT)
                .build();
        paymentRepository.save(payment);

        return new ReservationCreateResponseDto(saved.getId(), orderId, DEPOSIT_AMOUNT, saved.getStatus());
    }

    /**
     * 결제 미완료(PENDING) 예약을 PAYMENT_FAILED로 전환 + 좌석 복원 + payment 정리.
     * 스케줄러가 timeout 지난 예약을 발견했을 때 호출.
     */
    @Transactional
    public void expirePending(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }
        handlePendingFailure(reservation);
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = getActiveReservation(reservationId, userId);
        StoreRemain storeRemain = reservation.getStoreRemain();

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            // 결제 미완료: PAYMENT_FAILED로 기록 (사용자 예약 취소 내역과 구분)
            handlePendingFailure(reservation);
        } else {
            // 결제 완료(CONFIRMED): PortOne 환불 후 CANCELED로 변경
            paymentService.refundPayment(reservation);
            restoreInventory(reservation);
            reservation.changeStatus(ReservationStatus.CANCELED);
            kafkaTemplate.send("notification.reservation.canceled", new ReservationCanceledEvent(
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
        kafkaTemplate.send("notification.reservation.changed", new ReservationChangedEvent(
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

        // PENDING 예약에 대해 orderId를 단일 IN 쿼리로 일괄 조회 (결제 진행 버튼용)
        List<Long> pendingIds = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .map(Reservation::getId)
                .toList();

        Map<Long, String> orderIdByReservationId = new HashMap<>();
        if (!pendingIds.isEmpty()) {
            paymentRepository.findAllByReservationIdIn(pendingIds)
                    .forEach(p -> orderIdByReservationId.put(p.getReservation().getId(), p.getOrderId()));
        }

        return reservations.stream()
                .filter(r -> r.getStatus() != ReservationStatus.PAYMENT_FAILED)
                .map(reservation -> {
            StoreRemain storeRemain = reservation.getStoreRemain();
            Store store = storeRemain.getStore();
            String orderId = orderIdByReservationId.get(reservation.getId());

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
                    reservation.getCreatedAt(),
                    orderId
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
            kafkaTemplate.send("notification.reservation.visited", new ReservationVisitedEvent(
                    reservation.getId(),
                    reservation.getUser().getId(),
                    reservation.getStoreRemain().getStore().getStoreName(),
                    reservation.getStoreRemain().getRemainDate().toString(),
                    reservation.getStoreRemain().getRemainTime().toString()
            ));
        }
    }

    // Basic Logic
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
            throw new CustomException(ErrorCode.NOT_VISITABLE_STATUS);
        }

        reservation.changeStatus(ReservationStatus.VISITED);

        StoreRemain storeRemain = reservation.getStoreRemain();
        kafkaTemplate.send("notification.reservation.visited", new ReservationVisitedEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate().toString(),
                storeRemain.getRemainTime().toString()
        ));
    }

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

    /**
     * 결제 미완료 예약의 공통 정리 로직 (cancelReservation의 PENDING 분기 + expirePending 공용).
     * payment를 FAILED로 표시하고, 좌석을 복원하고, 예약 상태를 PAYMENT_FAILED로 전환한다.
     */
    private void handlePendingFailure(Reservation reservation) {
        paymentRepository.findByReservation_Id(reservation.getId())
                .ifPresent(Payment::markFailed);
        restoreInventory(reservation);
        reservation.changeStatus(ReservationStatus.PAYMENT_FAILED);
    }

    private void restoreInventory(Reservation reservation) {
        StoreRemain storeRemain = reservation.getStoreRemain();
        try {
            storeRemain.increaseRemainTeam();
            storeRemainRepository.saveAndFlush(storeRemain);
        } catch (OptimisticLockingFailureException e) {
            throw new CustomException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }

        kafkaTemplate.send("notification.vacancy.opened", new VacancyEvent(storeRemain.getId()));
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
