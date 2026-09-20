package com.example.library_management.dto.response;

import com.example.library_management.enums.BookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.library_management.enums.ReservationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {
    Long id;
    String title;
    String isbn;
    String author;
    String publisher;
    Integer publishYear;
    String language;
    String description;
    String coverImageUrl;
    Integer totalCopies;
    Integer availableCopies;
    // So ban dang duoc giu (Reservation o trang thai ACCEPTED), khong tinh PENDING
    Integer reservedCopies;
    // Trang thai dat sach cua CHINH nguoi dang xem (PENDING/ACCEPTED), null neu chua dat / da xu ly xong
    ReservationStatus myReservationStatus;
    Long myReservationId;
    // Chi co gia tri khi myReservationStatus = ACCEPTED - han den lay
    LocalDate myReservationExpiryDate;
    // Vi tri trong hang doi neu myReservationStatus = PENDING, null neu khong ap dung
    Integer myReservationQueuePosition;
    String shelfLocation;
    BookStatus status;
    CategoryResponse category;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
