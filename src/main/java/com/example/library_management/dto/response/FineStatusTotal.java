package com.example.library_management.dto.response;

import com.example.library_management.enums.FineStatus;

import java.math.BigDecimal;

/** Ket qua GROUP BY status cua Fine Report (dung cho JPQL constructor expression). */
public record FineStatusTotal(FineStatus status, Long count, BigDecimal amount) {
}
