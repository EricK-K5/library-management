package com.example.library_management.repository;

import com.example.library_management.entity.Fine;
import com.example.library_management.enums.FineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByUserId(String userId);

    List<Fine> findByStatus(FineStatus status);

    List<Fine> findByUserIdAndStatus(String userId, FineStatus status);

    Optional<Fine> findByBorrowRecordId(Long borrowRecordId);

    // Kiểm tra user còn khoản phạt chưa thanh toán không (chặn mượn sách mới nếu có)
    boolean existsByUserIdAndStatus(String userId, FineStatus status);
}
