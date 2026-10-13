package com.example.library_management.dto.response;

import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.enums.BorrowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowRecordResponse {

    Long id;

    UserResponse user;

    BookResponse book;

    LocalDate borrowDate;

    LocalDate dueDate;

    LocalDate returnDate;

    LocalDate lostDate;

    BorrowStatus status;

    // Số ngày quá hạn hiện tại; bằng 0 nếu không quá hạn.
    long overdueDays;

    String processedByUsername;

    String issuedByUsername;

    String receivedByUsername;

    Long reservationId;

    String note;

    List<FineResponse> fines;

    LocalDateTime createdAt;
}