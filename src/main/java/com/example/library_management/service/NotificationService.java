package com.example.library_management.service;

import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.util.MailFormat;
import com.example.library_management.util.OverduePolicy;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE )
public class NotificationService {
    static final List<Integer> REMIND_DAYS_BEFORE = List.of(3,1,0);

    static final int OVERDUE_REPEAT_DAYS = 2;

    BorrowRecordRepository borrowRecordRepository;
    FineRepository fineRepository;
    EmailService emailService;

    @Transactional
    public int sendDueSoonReminders() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dueDates = REMIND_DAYS_BEFORE.stream().map(today::plusDays).toList();

        int sent = 0;
        for (BorrowRecord b : borrowRecordRepository.findDueSoonToNotify(
                List.of(BorrowStatus.BORROWED), dueDates, today)) {
            long daysLeft = ChronoUnit.DAYS.between(today, b.getDueDate());

            String when = daysLeft == 0
                    ? "đến hạn trả vào HÔM NAY (" + MailFormat.date(b.getDueDate()) + ")"
                    : "còn " + daysLeft + " ngày nữa là đến hạn trả (" + MailFormat.date(b.getDueDate()) + ")";

            boolean ok = emailService.send(
                    b.getUser().getEmail(),
                    "Nhắc trả sách: " + b.getBook().getTitle(),
                    "Chào " + b.getUser().getFullName() + ",\n\n"
                            + "Sách \"" + b.getBook().getTitle() + "\" " + when + ".\n"
                            + "Vui lòng trả sách đúng hạn để tránh bị phạt.\n\n"
                            + "Thư viện");

            if (ok) {
                b.setLastNotifiedDate(today);
                sent++;
            }
        }
        return sent;
    }

    @Transactional
    public int sendOverdueNotices() {
        LocalDate today = LocalDate.now();

        int sent = 0;
        for (BorrowRecord b : borrowRecordRepository.findOverdueToNotify(
                List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE), today)) {
            long lateDays = OverduePolicy.lateDays(b.getDueDate(), today);
            if (lateDays != 1 && lateDays % OVERDUE_REPEAT_DAYS != 0) {
                continue;
            }

            Fine fine = fineRepository
                    .findByBorrowRecordIdAndReason(b.getId(), FineReason.OVERDUE)
                    .orElse(null);
            if (fine == null || fine.getStatus() != FineStatus.UNPAID) {
                continue;
            }

            boolean ok = emailService.send(
                    b.getUser().getEmail(),
                    "Sách quá hạn - tiền phạt " + MailFormat.money(fine.getAmount()),
                    "Chào " + b.getUser().getFullName() + ",\n\n"
                            + "Sách \"" + b.getBook().getTitle() + "\" đã quá hạn " + lateDays
                            + " ngày (hạn trả: " + MailFormat.date(b.getDueDate()) + ").\n"
                            + "Tiền phạt hiện tại: " + MailFormat.money(fine.getAmount())
                            + ". Số tiền này tiếp tục tăng cho đến khi bạn trả sách.\n"
                            + "Bạn chưa thể đặt hoặc mượn sách mới cho đến khi trả sách và thanh toán tiền phạt.\n"
                            + "Nếu quá hạn 30 ngày chưa trả, sách sẽ bị tính là mất và bạn phải đền sách.\n\n"
                            + "Thư viện");

            if (ok) {
                b.setLastNotifiedDate(today);
                sent++;
            }
        }
        return sent;
    }
}
