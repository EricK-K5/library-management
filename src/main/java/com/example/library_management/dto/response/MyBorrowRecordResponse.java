package com.example.library_management.dto.response;

import com.example.library_management.enums.BorrowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyBorrowRecordResponse {
    Long id;

    BookResponse book;

    LocalDate borrowDate;

    LocalDate dueDate;

    LocalDate lostDate;

    LocalDate returnDate;

    BorrowStatus status;

    // Tinh luc doc tu dueDate (khong cho scheduler): true neu dang giu sach va da qua han
    boolean overdue;

    long overdueDays;

    // Tien phat tam tinh neu tra hom nay (Fine that chi tao luc tra / mat sach)
    BigDecimal estimatedFine;

    String processedByUsername;

    // Thu thu giao sach / nhan lai sach (null voi record tao truoc khi co 2 truong nay)
    String issuedByUsername;

    String receivedByUsername;

    String note;

    Long reservationId;

    List<FineResponse> fines;

    LocalDateTime createdAt;
}
