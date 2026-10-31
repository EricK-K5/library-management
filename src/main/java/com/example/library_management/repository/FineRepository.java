package com.example.library_management.repository;

import com.example.library_management.entity.Fine;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.dto.response.FineReportRow;
import com.example.library_management.dto.response.FineStatusTotal;
import com.example.library_management.enums.BorrowStatus;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByUserId(String userId);

    // Khoa dong khi xac nhan thanh toan online
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fine f WHERE f.id = :id")
    Optional<Fine> findByIdForUpdate(@Param("id") Long id);

    List<Fine> findByStatus(FineStatus status);

    List<Fine> findByUserIdAndStatus(String userId, FineStatus status);

    // ===== Ban phan trang cho man hinh thu thu (GET /fines) =====
    // FineResponse doc user, borrowRecord.book va processedBy -> nap san ca 4 (deu to-one) trong cung 1 query, tranh N+1.
    @Override
    @EntityGraph(attributePaths = {"user", "borrowRecord", "borrowRecord.book", "processedBy"})
    Page<Fine> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "borrowRecord", "borrowRecord.book", "processedBy"})
    Page<Fine> findByUserId(String userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "borrowRecord", "borrowRecord.book", "processedBy"})
    Page<Fine> findByStatus(FineStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "borrowRecord", "borrowRecord.book", "processedBy"})
    Page<Fine> findByUserIdAndStatus(String userId, FineStatus status, Pageable pageable);

    // Mot borrow record co the co nhieu fine (moi reason toi da 1)
    List<Fine> findByBorrowRecordId(Long borrowRecordId);

    Optional<Fine> findByBorrowRecordIdAndReason(Long borrowRecordId, FineReason reason);

    boolean existsByBorrowRecordIdAndReason(Long borrowRecordId, FineReason reason);

    // Kiểm tra user còn khoản phạt chưa thanh toán không (chặn mượn sách mới nếu có)
    boolean existsByUserIdAndStatus(String userId, FineStatus status);

    @Query("""
    SELECT f
    FROM Fine f
    LEFT JOIN f.user u
    LEFT JOIN f.borrowRecord br
    LEFT JOIN br.book b
    WHERE (
        :keyword IS NULL OR
        LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
        LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
        LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    )
    AND (:status IS NULL OR f.status = :status)
    """)
    @EntityGraph(attributePaths = {
            "user",
            "borrowRecord",
            "borrowRecord.book",
            "processedBy"
    })
    Page<Fine> searchFines(
            @Param("keyword") String keyword,
            @Param("status") FineStatus status,
            Pageable pageable
    );

    //    Fines chưa thanh toán
    @Query("""
        SELECT COALESCE(SUM(f.amount), 0)
        FROM Fine f
        WHERE f.status = :status
        """)
    BigDecimal sumAmountByStatus(@Param("status") FineStatus status);

    // ========== BAO CAO PHAT (Fine Report) ==========
    // Ngay loc theo issuedDate (bao gom 2 dau). Phat "tam tinh" cua sach chua tra
    // (OVERDUE + UNPAID + luot muon dang BORROWED/OVERDUE) bi loai vi so tien con thay doi.
    // Chi JOIN quan he to-one (user, borrowRecord, book) nen khong nhan doi dong.
    String FINE_REPORT_FILTER = """
            WHERE (:startDate IS NULL OR f.issuedDate >= :startDate)
              AND (:endDate IS NULL OR f.issuedDate <= :endDate)
              AND (:status IS NULL OR f.status = :status)
              AND (:reason IS NULL OR f.reason = :reason)
              AND NOT (f.reason = :provisionalReason
                       AND f.status = :provisionalStatus
                       AND br.status IN :activeBorrowStatuses)
            """;

    @Query(value = """
            SELECT new com.example.library_management.dto.response.FineReportRow(
                f.id, u.id, u.fullName, b.title, f.reason, f.amount, f.status, f.issuedDate, f.paidDate)
            FROM Fine f
            JOIN f.user u
            JOIN f.borrowRecord br
            JOIN br.book b
            """ + FINE_REPORT_FILTER + """
            ORDER BY f.issuedDate ASC, f.id ASC
            """,
            countQuery = """
            SELECT COUNT(f)
            FROM Fine f
            JOIN f.borrowRecord br
            """ + FINE_REPORT_FILTER)
    Page<FineReportRow> findReportRowsInternal(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("status") FineStatus status,
                                               @Param("reason") FineReason reason,
                                               @Param("provisionalReason") FineReason provisionalReason,
                                               @Param("provisionalStatus") FineStatus provisionalStatus,
                                               @Param("activeBorrowStatuses") Collection<BorrowStatus> activeBorrowStatuses,
                                               Pageable pageable);

    @Query("""
            SELECT new com.example.library_management.dto.response.FineStatusTotal(
                f.status, COUNT(f), SUM(f.amount))
            FROM Fine f
            JOIN f.borrowRecord br
            """ + FINE_REPORT_FILTER + """
            GROUP BY f.status
            """)
    List<FineStatusTotal> sumReportTotalsInternal(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate,
                                                  @Param("status") FineStatus status,
                                                  @Param("reason") FineReason reason,
                                                  @Param("provisionalReason") FineReason provisionalReason,
                                                  @Param("provisionalStatus") FineStatus provisionalStatus,
                                                  @Param("activeBorrowStatuses") Collection<BorrowStatus> activeBorrowStatuses);

    default Page<FineReportRow> findReportRows(LocalDate startDate, LocalDate endDate,
                                               FineStatus status, FineReason reason, Pageable pageable) {
        return findReportRowsInternal(startDate, endDate, status, reason,
                FineReason.OVERDUE, FineStatus.UNPAID, ACTIVE_BORROW_STATUSES, pageable);
    }

    default List<FineStatusTotal> sumReportTotals(LocalDate startDate, LocalDate endDate,
                                                  FineStatus status, FineReason reason) {
        return sumReportTotalsInternal(startDate, endDate, status, reason,
                FineReason.OVERDUE, FineStatus.UNPAID, ACTIVE_BORROW_STATUSES);
    }

    // Luot muon chua ket thuc: phat OVERDUE cua no con tam tinh
    List<BorrowStatus> ACTIVE_BORROW_STATUSES = List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE);
}
