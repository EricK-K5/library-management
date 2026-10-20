package com.example.library_management.event;

import com.example.library_management.service.EmailService;
import com.example.library_management.util.MailFormat;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class MailEventListener {
    EmailService emailService;

    @Async
//    chi chay khi transaction commit thanh cong, neu transaction rollback thi khong chay
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,fallbackExecution = true)
    public void onReservationAccept(MailEvents.ReservationAccept e){
        emailService.send(e.email(), "Đặt sách thành công - sách đã được giữ cho bạn",
                "Chào " + e.fullName() + ",\n\n"
                        + "Sách \"" + e.bookTitle() + "\" đã được giữ cho bạn.\n"
                        + "Vui lòng đến thư viện nhận sách trước hết ngày " + MailFormat.date(e.expiryDate())
                        + ". Quá hạn này lượt đặt sẽ tự động bị hủy.\n\n"
                        + "Thư viện");
    }
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationQueued(MailEvents.ReservationQueued e) {
        String position = e.queuePosition() != null
                ? " Vị trí hiện tại của bạn trong hàng đợi: " + e.queuePosition() + "."
                : "";
        emailService.send(e.email(), "Đặt sách thành công - bạn đang trong hàng đợi",
                "Chào " + e.fullName() + ",\n\n"
                        + "Sách \"" + e.bookTitle() + "\" hiện đã hết nên bạn được xếp vào hàng đợi." + position + "\n"
                        + "Khi đến lượt, hệ thống sẽ giữ sách cho bạn và gửi mail thông báo.\n\n"
                        + "Thư viện");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookBorrowed(MailEvents.BookBorrowed e) {
        emailService.send(e.email(), "Xác nhận mượn sách",
                "Chào " + e.fullName() + ",\n\n"
                        + "Bạn đã mượn sách \"" + e.bookTitle() + "\" ngày " + MailFormat.date(e.borrowDate()) + ".\n"
                        + "Hạn trả: " + MailFormat.date(e.dueDate()) + ".\n"
                        + "Trả trễ sẽ bị tính tiền phạt theo từng ngày.\n\n"
                        + "Thư viện");
    }
}
