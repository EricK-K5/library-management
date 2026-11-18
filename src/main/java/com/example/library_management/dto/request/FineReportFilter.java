package com.example.library_management.dto.request;

import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;

import java.time.LocalDate;

public record FineReportFilter(LocalDate startDate, LocalDate endDate, FineStatus status, FineReason fineType) {
}
