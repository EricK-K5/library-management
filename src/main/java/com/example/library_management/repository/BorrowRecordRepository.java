package com.example.library_management.repository;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // Danh sach qua han (BORROWED/OVERDUE co dueDate < today), tre lau nhat len dau.
    // Loc theo dueDate chu khong theo status de dung ngay ca khi scheduler chua kip chay.
    // keyword (da lowercase + co %...%, hoac null = khong loc) khop username / fullName cua user hoac title cua sach.
    // Phan trang: JOIN FETCH chi cho quan he to-one (user, book) nen phan trang van chay tren DB; countQuery rieng.
    @Query(value = """
            SELECT b FROM BorrowRecord b
            JOIN FETCH b.user u JOIN FETCH b.book bk
            WHERE b.status IN :statuses AND b.dueDate < :today
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            ORDER BY b.dueDate ASC, b.id ASC
            """,
            countQuery = """
            SELECT COUNT(b) FROM BorrowRecord b
            JOIN b.user u JOIN b.book bk
            WHERE b.status IN :statuses AND b.dueDate < :today
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            """)
    Page<BorrowRecord> findOverdue(@Param("statuses") Collection<BorrowStatus> statuses,
                                   @Param("today") LocalDate today,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    // Danh sach tat ca, loc tuy chon theo status (status luu trong DB) va keyword (nhu findOverdue).
    // status = null / keyword = null thi bo qua dieu kien do. Sort lay tu Pageable (mac dinh id DESC o controller).
    @Query(value = """
            SELECT b FROM BorrowRecord b
            JOIN FETCH b.user u JOIN FETCH b.book bk
            WHERE (:status IS NULL OR b.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            """,
            countQuery = """
            SELECT COUNT(b) FROM BorrowRecord b
            JOIN b.user u JOIN b.book bk
            WHERE (:status IS NULL OR b.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.username) LIKE :keyword
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(bk.title) LIKE :keyword)
            """)
    Page<BorrowRecord> search(@Param("status") BorrowStatus status,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    // findAll co phan trang, nap san user + book trong cung 1 query (tranh N+1)
    @Override
    @EntityGraph(attributePaths = {"user", "book"})
    Page<BorrowRecord> findAll(Pageable pageable);

    // Chan muon/dat khi user dang tre han, tinh theo dueDate (khong phu thuoc status da duoc scheduler cap nhat chua)
    boolean existsByUserIdAndStatusInAndDueDateBefore(String userId, Collection<BorrowStatus> statuses, LocalDate date);

    // Job dem ngay: BORROWED -> OVERDUE bang 1 cau UPDATE.
    // Neu returnBook/markLost dang giu lock record, UPDATE se cho va doc lai ban moi nhat nen khong ghi de RETURNED/LOST.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BorrowRecord b SET b.status = :overdue
            WHERE b.status = :borrowed AND b.dueDate < :today
            """)
    int markOverdue(@Param("overdue") BorrowStatus overdue,
                    @Param("borrowed") BorrowStatus borrowed,
                    @Param("today") LocalDate today);

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

    List<BorrowRecord> findByStatusInAndDueDateBefore(
            Collection<BorrowStatus> statuses,
            LocalDate dueDate
    );
}