package com.catchtable.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortonePaymentResponse(
        String id,
        String status,
        Amount amount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(Integer total) {}

    public boolean isPaid() {
        return "PAID".equals(status);
    }
}
