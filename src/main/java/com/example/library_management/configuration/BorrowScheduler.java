package com.example.library_management.configuration;

import com.example.library_management.service.BorrowRecordService;
import com.example.library_management.service.FineService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class BorrowScheduler {

    BorrowRecordService borrowRecordService;
    FineService fineService;

    // Chạy mỗi ngày lúc 00:10:
    // 1. Đánh dấu BORROWED đã quá hạn thành OVERDUE.
    // 2. Cập nhật Fine quá hạn thông qua FineService.
    // 3. Tự động đánh dấu LOST cho sách quá hạn đủ 30 ngày.
    @Scheduled(cron = "0 10 0 * * *")
    public void processOverdueAndLostBooks() {

        int overdue = borrowRecordService.markOverdueRecords();

        log.info(
                "Scheduled job: marked {} borrow record(s) as OVERDUE",
                overdue
        );

        int refreshed = fineService.refreshOverdueFineEstimates();

        log.info(
                "Scheduled job: refreshed overdue fines for {} record(s)",
                refreshed
        );

        for (Long id : borrowRecordService.findLongOverdueRecordIds()) {
            try {
                borrowRecordService.autoMarkLost(id);

                log.info(
                        "Scheduled job: borrow record {} auto-marked as LOST",
                        id
                );
            } catch (Exception e) {
                log.error(
                        "Scheduled job: failed to auto-mark borrow record {} as LOST",
                        id,
                        e
                );
            }
        }
    }

    // Chạy bù khi ứng dụng khởi động.
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        log.info("Startup catch-up: processing overdue / lost borrow records");
        processOverdueAndLostBooks();
    }
}