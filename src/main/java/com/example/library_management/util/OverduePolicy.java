package com.example.library_management.util;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.enums.BorrowStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class OverduePolicy {

    private OverduePolicy() {
    }

    // Số ngày quá hạn; ngày đến hạn vẫn được xem là hợp lệ.
    public static long lateDays(LocalDate dueDate, LocalDate today) {
        if (dueDate == null || today == null) {
            return 0;
        }

        return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }

    // Chỉ tính ngày quá hạn khi bản ghi vẫn đang giữ sách.
    public static long overdueDays(BorrowRecord record) {
        if (record == null || record.getDueDate() == null) {
            return 0;
        }

        boolean holding =
                record.getStatus() == BorrowStatus.BORROWED
                        || record.getStatus() == BorrowStatus.OVERDUE;

        if (!holding) {
            return 0;
        }

        return lateDays(record.getDueDate(), LocalDate.now());
    }
}