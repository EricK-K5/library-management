package com.example.library_management.dto.response;

import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FineReportResponse {
    LocalDateTime generatedAt;

    // Bo loc da ap dung (null = ALL / khong gioi han). Ngay loc theo ngay ghi nhan phat.
    LocalDate startDate;
    LocalDate endDate;
    FineStatus status;
    FineReason fineType;

    // Summary tinh tren TOAN BO ket qua da loc (khong phu thuoc phan trang)
    FineReportSummary summary;
    Page<FineReportRow> details;
}
