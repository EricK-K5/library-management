package com.example.library_management.dto.response;

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

    String processedByUsername;

    // Neu luot muon nay den tu 1 reservation da duoc accept truoc do, null neu muon truc tiep
    Long reservationId;

    String note;

    List<FineResponse> fines;

    LocalDateTime createdAt;
}
