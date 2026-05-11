package com.catchtable.payment.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.notification.event.ReservationConfirmedEvent;
import com.catchtable.payment.dto.PortonePaymentResponse;
import com.catchtable.payment.entity.Payment;
import com.catchtable.payment.entity.PaymentStatus;
import com.catchtable.payment.repository.PaymentRepository;
import com.catchtable.remain.entity.StoreRemain;
import com.catchtable.reservation.entity.Reservation;
import com.catchtable.reservation.entity.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient portoneRestClient;

    @Transactional
    public void confirmPayment(String paymentId, Long userId) {
        Payment payment = paymentRepository.findByOrderIdWithAllForUpdate(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        Reservation reservation = payment.getReservation();
        if (!reservation.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        PortonePaymentResponse portoneResponse = verifyWithPortone(paymentId);

        if (!portoneResponse.isPaid()) {
            payment.markFailed();
            throw new CustomException(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        if (portoneResponse.amount() == null || !payment.getAmount().equals(portoneResponse.amount().total())) {
            payment.markFailed();
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        payment.markPaid(portoneResponse.id());
        reservation.changeStatus(ReservationStatus.CONFIRMED);

        StoreRemain storeRemain = reservation.getStoreRemain();
        eventPublisher.publishEvent(new ReservationConfirmedEvent(
                reservation.getId(),
                reservation.getUser().getId(),
                storeRemain.getStore().getStoreName(),
                storeRemain.getRemainDate().toString(),
                storeRemain.getRemainTime().toString()
        ));
    }

    @Transactional
    public void refundPayment(Reservation reservation) {
        Payment payment = paymentRepository.findByReservation_Id(reservation.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PAID) {
            return;
        }

        try {
            portoneRestClient.post()
                    .uri("/payments/{paymentId}/cancel", payment.getPortonePaymentId())
                    .body(new PortoneCancelRequest("고객 요청 취소"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.PAYMENT_REFUND_FAILED);
        }

        payment.markCanceled();
    }

    private PortonePaymentResponse verifyWithPortone(String orderId) {
        try {
            return portoneRestClient.get()
                    .uri("/payments/{orderId}", orderId)
                    .retrieve()
                    .body(PortonePaymentResponse.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.PAYMENT_PORTONE_API_ERROR);
        }
    }

    private record PortoneCancelRequest(String reason) {}
}
