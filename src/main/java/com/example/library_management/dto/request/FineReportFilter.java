package com.example.library_management.dto.request;

import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;

import java.time.LocalDate;

/**
 * Bo loc da duoc validate cua Fine Report. null = khong loc (ALL / khong gioi han).
 * Ngay loc theo Fine.issuedDate, bao gom ca startDate va endDate.
 */
public record FineReportFilter(LocalDate startDate, LocalDate endDate, FineStatus status, FineReason fineType) {
}
