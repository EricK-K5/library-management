package com.example.library_management.dto.response;

public record PaymentResponse(
        String paymentUrl,
        String txnRef
) {
}
