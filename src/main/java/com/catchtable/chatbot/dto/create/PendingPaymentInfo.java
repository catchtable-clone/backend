package com.catchtable.chatbot.dto.create;

public record PendingPaymentInfo(
        Long reservationId,
        String orderId,
        int amount
) {
}
