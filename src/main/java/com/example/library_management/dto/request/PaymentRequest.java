package com.example.library_management.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        String orderInfo
) {
}
