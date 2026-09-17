package com.example.library_management.repository;

import com.example.library_management.entity.Reservation;
import com.example.library_management.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserId(String userId);

    List<Reservation> findByBookId(Long bookId);

    List<Reservation> findByStatus(ReservationStatus status);

    // Hang doi dat truoc cua 1 quyen sach, dau hang = createdAt som nhat, id nho nhat neu trung
    List<Reservation> findByBookIdAndStatusOrderByCreatedAtAscIdAsc(Long bookId, ReservationStatus status);

    // Lay ban ghi PENDING/ACCEPTED dau hang, dung pessimistic lock de tranh 2 thread cung promote 1 luc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.book.id = :bookId AND r.status = :status
            ORDER BY r.createdAt ASC, r.id ASC
            """)
    List<Reservation> findQueueForUpdate(@Param("bookId") Long bookId, @Param("status") ReservationStatus status);

    Optional<Reservation> findByUserIdAndBookIdAndStatus(String userId, Long bookId, ReservationStatus status);

    boolean existsByUserIdAndBookIdAndStatusIn(String userId, Long bookId, Collection<ReservationStatus> statuses);

    List<Reservation> findByUserIdAndBookIdAndStatusIn(String userId, Long bookId, Collection<ReservationStatus> statuses);

    long countByUserIdAndStatusIn(String userId, Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusAndExpiryDateBefore(ReservationStatus status, LocalDate date);

    boolean existsByBookIdAndStatus(Long bookId, ReservationStatus status);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);
}
