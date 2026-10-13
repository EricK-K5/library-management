package com.example.library_management.repository;

import com.example.library_management.entity.Fine;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByUserId(String userId);

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
}
