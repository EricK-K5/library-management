package com.example.library_management.configuration;

import com.example.library_management.service.BorrowRecordService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class BorrowScheduler {

    BorrowRecordService borrowRecordService;

    // Chay moi ngay luc 00:10 (sau ReservationScheduler luc 00:05):
    //  1) BORROWED da qua han -> OVERDUE
    //  2) OVERDUE qua 30 ngay chua tra -> tu dong LOST (tao Fine tre han + Fine den sach)
    // Dung so sanh bat dang thuc nen neu app tat vai ngay thi lan chay sau se bu lai.
    @Scheduled(cron = "0 10 0 * * *")
    public void processOverdueAndLostBooks() {
        int overdue = borrowRecordService.markOverdueRecords();
        log.info("Scheduled job: marked {} borrow record(s) as OVERDUE", overdue);

        // Moi record 1 transaction rieng (goi qua proxy cua bean khac), 1 record loi khong lam hong ca batch
        for (Long id : borrowRecordService.findLongOverdueRecordIds()) {
            try {
                borrowRecordService.autoMarkLost(id);
                log.info("Scheduled job: borrow record {} auto-marked as LOST", id);
            } catch (Exception e) {
                log.error("Scheduled job: failed to auto-mark borrow record {} as LOST", id, e);
            }
        }
    }
}
