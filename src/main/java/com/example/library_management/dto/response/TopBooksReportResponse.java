package com.example.library_management.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopBooksReportResponse {
    LocalDateTime generatedAt;

    // Bo loc da ap dung. Ngay loc theo ngay giao sach (BorrowRecord.borrowDate).
    LocalDate startDate;
    LocalDate endDate;
    Long categoryId;
    String categoryName;
    // null = tat ca
    Integer limit;

    // Summary tinh tren TOAN BO ket qua da loc, khong bi cat boi limit
    TopBooksSummary summary;
    List<BookRankingRow> rankings;
}
