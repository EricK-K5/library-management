package com.example.library_management.repository;

import com.example.library_management.entity.Reservation;
import com.example.library_management.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(String userId);

    List<Reservation> findByBookId(String bookId);

    List<Reservation> findByStatus(ReservationStatus status);

    // Hàng đợi đặt trước của 1 quyển sách, sắp theo thứ tự queuePosition
    List<Reservation> findByBookIdAndStatusOrderByQueuePositionAsc(String bookId, ReservationStatus status);

    Optional<Reservation> findByUserIdAndBookIdAndStatus(String userId, String bookId, ReservationStatus status);

    List<Reservation> findByStatusAndExpiryDateBefore(ReservationStatus status, LocalDate date);
}
