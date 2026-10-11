package com.example.library_management.util;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.enums.BorrowStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Quy tac tre han dung chung cho: tao Fine that (BorrowRecordService) va tinh tam khi doc (mapper).
// "Qua han" duoc suy ra tu dueDate, KHONG phu thuoc status trong DB (status chi duoc scheduler cap nhat moi ngay).
public final class OverduePolicy {

    // 5.000 VND/ngay tre, tran 30 ngay = 150.000 VND
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("5000");
    public static final int MAX_OVERDUE_FINE_DAYS = 30;

    private OverduePolicy() {
    }

    public record OverdueInfo(boolean overdue, long overdueDays, BigDecimal estimatedFine) {
    }

    // So ngay tre (0 neu chua qua han). Qua han khi today > dueDate (ngay dueDate van con hop le).
    public static long lateDays(LocalDate dueDate, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, today));
    }

    public static BigDecimal fineFor(long lateDays) {
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(Math.min(lateDays, MAX_OVERDUE_FINE_DAYS)));
    }

    // Chi record dang giu sach (BORROWED/OVERDUE) moi co the "dang tre han"; RETURNED/LOST da chot nen tra ve 0.
    public static OverdueInfo of(BorrowRecord record) {
        boolean holding = record.getStatus() == BorrowStatus.BORROWED
                || record.getStatus() == BorrowStatus.OVERDUE;
        long days = holding ? lateDays(record.getDueDate(), LocalDate.now()) : 0;
        return new OverdueInfo(days > 0, days, fineFor(days));
    }
}
