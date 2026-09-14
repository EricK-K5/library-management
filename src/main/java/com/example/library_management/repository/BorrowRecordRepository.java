package com.example.library_management.repository;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.enums.BorrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    List<BorrowRecord> findByUserId(String userId);

    List<BorrowRecord> findByBookId(Long bookId);

    List<BorrowRecord> findByStatus(BorrowStatus status);

    List<BorrowRecord> findByUserIdAndStatus(String userId, BorrowStatus status);

    // Đang mượn nhưng quá hạn (chưa đổi status sang OVERDUE)
    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowStatus status, LocalDate date);

    // dem so sach dang muon => check total>5?
    long countByUserIdAndStatus(String userId, BorrowStatus status);
}
