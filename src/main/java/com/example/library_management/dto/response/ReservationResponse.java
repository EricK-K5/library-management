package com.example.library_management.dto.response;

import com.example.library_management.enums.ReservationStatus;
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
public class ReservationResponse {
    Long id;

    UserResponse user;

    BookResponse book;

    LocalDate reservationDate;

    LocalDate expiryDate;
    // chi co y nghia khi status = ACCEPTED
    ReservationStatus status;

    String processedByUsername;

    // Vi tri trong hang doi khi status = PENDING (1 = ke tiep se duoc accept khi co suat trong),
    // null neu khong o trang thai PENDING (da ACCEPTED/da xu ly xong thi khong con xep hang nua)
    Integer queuePosition;

    LocalDateTime createdAt;
}
