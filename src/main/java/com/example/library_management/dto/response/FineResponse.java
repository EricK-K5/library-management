package com.example.library_management.dto.response;

import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineResponse {
    Long id;
    BigDecimal amount;
    FineReason reason;
    FineStatus status;
    LocalDate issuedDate;
    LocalDate paidDate;
    String note;
}
