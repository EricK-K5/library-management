package com.example.library_management.dto.response;

import com.example.library_management.enums.FineStatus;

import java.math.BigDecimal;

public record FineStatusTotal(FineStatus status, Long count, BigDecimal amount) {
}
