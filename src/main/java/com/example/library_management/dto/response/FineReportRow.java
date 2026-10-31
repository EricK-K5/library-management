package com.example.library_management.dto.response;

import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 1 dong chi tiet cua Fine Report. Thu tu field phai khop constructor trong query JPQL. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FineReportRow {
    Long fineId;
    String memberId;
    String memberName;
    String bookTitle;
    FineReason fineType;
    BigDecimal amount;
    FineStatus status;
    // Ngay ghi nhan phat (Fine.issuedDate)
    LocalDate issuedDate;
    // Chi co khi status = PAID
    LocalDate paidDate;
}
