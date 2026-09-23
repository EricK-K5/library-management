package com.example.library_management.repository;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    List<BorrowRecord> findByUserId(String userId);

    List<BorrowRecord> findByBookId(Long bookId);

    List<BorrowRecord> findByStatus(BorrowStatus status);

    List<BorrowRecord> findByUserIdAndStatus(String userId, BorrowStatus status);

    // Đang mượn nhưng quá hạn
    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowStatus status, LocalDate date);

    // Dung cho job tu dong danh dau LOST
    List<BorrowRecord> findByStatusAndDueDateLessThanEqual(BorrowStatus status, LocalDate date);

    // dem so sach dang muon => check total>5?
    long countByUserIdAndStatus(String userId, BorrowStatus status);

    // Chan muon/dat khi user con sach qua han chua tra
    boolean existsByUserIdAndStatus(String userId, BorrowStatus status);

    // Khoa record khi tra / bao mat / tu dong danh dau LOST, tranh scheduler va thu thu xu ly cung luc.
    // Thu tu khoa: BorrowRecord -> Book
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BorrowRecord b WHERE b.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);

    // So slot ma user dang chiem
    // Reservation (PENDING/ACCEPTED) duoc dem rieng o ReservationRepository.
    @Query("""
            SELECT COUNT(b) FROM BorrowRecord b
            WHERE b.user.id = :userId
              AND (b.status IN :activeStatuses
                   OR (b.status = :lostStatus
                       AND EXISTS (SELECT f.id FROM Fine f
                                   WHERE f.borrowRecord = b AND f.status = :unpaidStatus)))
            """)
    long countSlotsInUse(@Param("userId") String userId,
                         @Param("activeStatuses") Collection<BorrowStatus> activeStatuses,
                         @Param("lostStatus") BorrowStatus lostStatus,
                         @Param("unpaidStatus") FineStatus unpaidStatus);

    default long countBorrowSlotsInUse(String userId) {
        return countSlotsInUse(
                userId,
                List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE),
                BorrowStatus.LOST,
                FineStatus.UNPAID);
    }
}
