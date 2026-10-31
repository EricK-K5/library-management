package com.example.library_management.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FineReportSummary {
    // = paid + unpaid + waived (moi fine chi nam o dung 1 trang thai)
    BigDecimal totalRecordedAmount;
    BigDecimal totalPaidAmount;
    BigDecimal totalUnpaidAmount;
    BigDecimal totalWaivedAmount;
    long totalFineRecords;
}
