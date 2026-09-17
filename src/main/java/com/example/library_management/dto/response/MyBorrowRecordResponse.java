package com.example.library_management.dto.response;

import com.example.library_management.enums.BorrowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyBorrowRecordResponse {
    Long id;
    BookResponse book;
    LocalDate borrowDate;
    LocalDate dueDate;
    LocalDate returnDate;
    BorrowStatus status;
    String processedByUsername;
    String note;
    Long reservationId;
    FineResponse fine;
    LocalDateTime createdAt;
}
