package com.example.library_management.service;

import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.request.HandoverRequest;
import com.example.library_management.dto.request.MarkLostRequest;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Reservation;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.event.MailEvents;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.BorrowRecordMapper;
import com.example.library_management.mapper.MyBorrowRecordMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.UserRepository;
import com.example.library_management.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class BorrowRecordService {

    // Thời hạn mượn mặc định: 7 ngày.
    private static final int BORROW_DAYS = 7;

    // Hạn mức mặc định: 5 suất sách cho mỗi người dùng.
    private static final int DEFAULT_BORROW_LIMIT = 5;

    // Quá hạn 30 ngày mà chưa trả thì tự động đánh dấu mất sách.
    private static final int AUTO_LOST_AFTER_DAYS = 30;

    private static final Set<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            Set.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.ACCEPTED
            );

    BorrowRecordRepository borrowRecordRepository;
    BookRepository bookRepository;
    UserRepository userRepository;
    ReservationRepository reservationRepository;
    ReservationService reservationService;
    FineService fineService;
    BorrowRecordMapper borrowRecordMapper;
    MyBorrowRecordMapper myBorrowRecordMapper;
    ApplicationEventPublisher eventPublisher;

    // Chỉ LIBRARIAN/ADMIN có quyền borrow:manage.
    // Member phải đặt sách thành công trước khi nhân viên xác nhận giao sách.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BorrowRecordResponse handoverBook(HandoverRequest request) {
        User staff = getCurrentUser();
        LocalDate today = LocalDate.now();

        // Khóa Reservation để tránh giao sách đồng thời hoặc giao một Reservation đã xử lý.
        Reservation reservation =
                reservationService.lockReservation(request.getReservationId());

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            throw new AppException(ErrorCode.RESERVATION_NOT_ACCEPTED);
        }

        if (reservation.getStatus() != ReservationStatus.ACCEPTED) {
            throw new AppException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }

        // Reservation còn hiệu lực đến hết expiryDate.
        if (reservation.getExpiryDate() != null
                && reservation.getExpiryDate().isBefore(today)) {
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        }

        User member = reservation.getUser();

        // Không cho mượn nếu người dùng còn tiền phạt chưa thanh toán.
        if (fineService.hasUnpaidFine(member.getId())) {
            throw new AppException(ErrorCode.USER_HAS_UNPAID_FINE);
        }

        // Kiểm tra quá hạn trực tiếp bằng dueDate, không phụ thuộc Scheduler.
        if (borrowRecordRepository.existsByUserIdAndStatusInAndDueDateBefore(
                member.getId(),
                List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE),
                today
        )) {
            throw new AppException(ErrorCode.USER_HAS_OVERDUE_BOOK);
        }

        // Reservation ACCEPTED đã chiếm một suất sách.
        // Khi giao sách, Reservation chuyển thành FULFILLED và BorrowRecord chiếm suất đó.
        long slots =
                borrowRecordRepository.countBorrowSlotsInUse(member.getId())
                        + reservationRepository.countByUserIdAndStatusIn(
                        member.getId(),
                        ACTIVE_RESERVATION_STATUSES
                );

        if (slots > limitOf(member)) {
            throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
        }

        // Số lượng sách đã được giữ từ lúc Reservation được ACCEPTED.
        // Không giảm availableCopies lần thứ hai tại đây.
        reservation.setStatus(ReservationStatus.FULFILLED);
        reservation.setProcessedBy(staff);
        reservationRepository.save(reservation);

        BorrowRecord borrowRecord = BorrowRecord.builder()
                .user(member)
                .book(reservation.getBook())
                .borrowDate(today)
                .dueDate(today.plusDays(BORROW_DAYS))
                .processedBy(staff)
                .issuedBy(staff)
                .reservation(reservation)
                .build();

        BorrowRecord saved = borrowRecordRepository.save(borrowRecord);

//        gui mail
        eventPublisher.publishEvent(new MailEvents.BookBorrowed(
                member.getEmail(),
                member.getFullName(),
                reservation.getBook().getTitle(),
                saved.getBorrowDate(),
                saved.getDueDate()
        ));

        return borrowRecordMapper.toBorrowRecordResponse(saved);
    }

    // ==================== TRẢ SÁCH ====================

    @Transactional
    public BorrowRecordResponse returnBook(
            Long borrowRecordId,
            BorrowReturnRequest request
    ) {
        User processedBy = getCurrentUser();

        // Thống nhất thứ tự khóa: BorrowRecord trước, Book sau.
        BorrowRecord borrowRecord = borrowRecordRepository
                .findByIdForUpdate(borrowRecordId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED)
                );

        if (borrowRecord.getStatus() == BorrowStatus.RETURNED) {
            throw new AppException(ErrorCode.BORROW_ALREADY_RETURNED);
        }

        if (borrowRecord.getStatus() == BorrowStatus.LOST) {
            throw new AppException(ErrorCode.BORROW_ALREADY_LOST);
        }

        LocalDate today = LocalDate.now();

        borrowRecord.setReturnDate(today);
        borrowRecord.setStatus(BorrowStatus.RETURNED);
        borrowRecord.setProcessedBy(processedBy);
        borrowRecord.setReceivedBy(processedBy);

        if (request != null && StringUtils.hasText(request.getNote())) {
            borrowRecord.setNote(request.getNote());
        }

        // FineService chịu trách nhiệm tạo/cập nhật tiền phạt quá hạn.
        // Thực hiện trước khi đẩy hàng đợi để quy tắc kiểm tra tiền phạt
        // được áp dụng khi hệ thống xét người tiếp theo.
        fineService.createOrUpdateOverdueFine(borrowRecord, today);

        // Trả sách về kho và đẩy người đầu hàng đợi lên ACCEPTED nếu có.
        reservationService.releaseAndPromote(
                borrowRecord.getBook().getId()
        );

        return borrowRecordMapper.toBorrowRecordResponse(
                borrowRecordRepository.save(borrowRecord)
        );
    }

    // ==================== ĐÁNH DẤU MẤT SÁCH ====================

    @Transactional
    public BorrowRecordResponse markLost(
            Long borrowRecordId,
            MarkLostRequest request
    ) {
        User staff = getCurrentUser();

        BorrowRecord borrowRecord = borrowRecordRepository
                .findByIdForUpdate(borrowRecordId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED)
                );

        String note = request != null ? request.getNote() : null;
        BigDecimal lostAmount =
                request != null ? request.getLostAmount() : null;

        return borrowRecordMapper.toBorrowRecordResponse(
                applyLost(borrowRecord, staff, note, lostAmount, false)
        );
    }

    // ==================== SCHEDULER: CẬP NHẬT TRẠNG THÁI ====================

    // Chuyển BORROWED thành OVERDUE khi đã qua hạn trả.
    // Phần tính tiền phạt được xử lý riêng trong FineService.
    @Transactional
    public int markOverdueRecords() {
        return borrowRecordRepository.markOverdue(
                BorrowStatus.OVERDUE,
                BorrowStatus.BORROWED,
                LocalDate.now()
        );
    }

    // Lấy các lượt mượn OVERDUE đã quá hạn ít nhất 30 ngày.
    @Transactional(readOnly = true)
    public List<Long> findLongOverdueRecordIds() {
        LocalDate threshold =
                LocalDate.now().minusDays(AUTO_LOST_AFTER_DAYS);

        return borrowRecordRepository
                .findByStatusAndDueDateLessThanEqual(
                        BorrowStatus.OVERDUE,
                        threshold
                )
                .stream()
                .map(BorrowRecord::getId)
                .toList();
    }

    // Mỗi record được xử lý trong transaction riêng.
    @Transactional
    public void autoMarkLost(Long borrowRecordId) {
        BorrowRecord borrowRecord = borrowRecordRepository
                .findByIdForUpdate(borrowRecordId)
                .orElse(null);

        // Nếu record đã được trả hoặc xử lý mất trước đó thì bỏ qua.
        if (borrowRecord == null
                || borrowRecord.getStatus() != BorrowStatus.OVERDUE) {
            return;
        }

        applyLost(
                borrowRecord,
                null,
                "Tự động: quá hạn "
                        + AUTO_LOST_AFTER_DAYS
                        + " ngày chưa trả sách",
                null,
                true
        );
    }

    // ==================== LOGIC XỬ LÝ MẤT SÁCH ====================

    // Chỉ gọi sau khi đã khóa BorrowRecord.
    // FineService xử lý các khoản phạt.
    // Phần này chỉ cập nhật trạng thái BorrowRecord và số lượng sách.
    private BorrowRecord applyLost(
            BorrowRecord borrowRecord,
            User processedBy,
            String note,
            BigDecimal lostAmountOverride,
            boolean auto
    ) {
        if (borrowRecord.getStatus() == BorrowStatus.LOST) {
            throw new AppException(ErrorCode.BORROW_ALREADY_LOST);
        }

        if (borrowRecord.getStatus() != BorrowStatus.BORROWED
                && borrowRecord.getStatus() != BorrowStatus.OVERDUE) {
            throw new AppException(ErrorCode.BORROW_CANNOT_MARK_LOST);
        }

        LocalDate today = LocalDate.now();

        // Khóa sách sau khi đã khóa BorrowRecord.
        Book book = bookRepository
                .findByIdForUpdate(borrowRecord.getBook().getId())
                .orElseThrow(() ->
                        new AppException(ErrorCode.BOOK_NOT_EXISTED)
                );

        borrowRecord.setStatus(BorrowStatus.LOST);
        borrowRecord.setLostDate(today);
        borrowRecord.setProcessedBy(processedBy);

        if (StringUtils.hasText(note)) {
            borrowRecord.setNote(note);
        }

        // FineService xử lý tiền phạt quá hạn đến ngày mất sách.
        fineService.createOrUpdateOverdueFine(borrowRecord, today);

        // FineService xử lý tiền đền sách và phí xử lý.
        fineService.createOrUpdateLostBookFine(
                borrowRecord,
                book,
                lostAmountOverride,
                today,
                auto
        );

        // Sách đã mất không quay lại kho.
        // Chỉ giảm tổng số bản, không tăng số bản đang có sẵn.
        book.setTotalCopies(
                Math.max(0, book.getTotalCopies() - 1)
        );

        applyComputedStatus(book);
        bookRepository.save(book);

        return borrowRecordRepository.save(borrowRecord);
    }

    // ==================== TRA CỨU BORROW RECORD ====================

    // Danh sách tất cả lượt mượn, có lọc trạng thái và từ khóa.
    @Transactional(readOnly = true)
    public Page<BorrowRecordResponse> getAllBorrowRecords(
            BorrowStatus status,
            String keyword,
            Pageable pageable
    ) {
        return borrowRecordRepository
                .search(status, toLikePattern(keyword), pageable)
                .map(borrowRecordMapper::toBorrowRecordResponse);
    }

    // Danh sách lượt mượn thực tế đã quá hạn.
    // Không phụ thuộc việc Scheduler đã cập nhật status hay chưa.
    @Transactional(readOnly = true)
    public Page<BorrowRecordResponse> getOverdueRecords(
            String keyword,
            Pageable pageable
    ) {
        return borrowRecordRepository
                .findOverdue(
                        List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE),
                        LocalDate.now(),
                        toLikePattern(keyword),
                        pageable
                )
                .map(borrowRecordMapper::toBorrowRecordResponse);
    }

    // Chi tiết một lượt mượn.
    @Transactional(readOnly = true)
    public BorrowRecordResponse getBorrowRecordById(Long id) {
        BorrowRecord borrowRecord = borrowRecordRepository
                .findById(id)
                .orElseThrow(() ->
                        new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED)
                );

        return borrowRecordMapper.toBorrowRecordResponse(borrowRecord);
    }

    // Danh sách lượt mượn của một người dùng cụ thể.
    @Transactional(readOnly = true)
    public List<BorrowRecordResponse> getBorrowRecordsByUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USERS_NOT_EXISTED);
        }

        return borrowRecordRepository.findByUserId(userId)
                .stream()
                .map(borrowRecordMapper::toBorrowRecordResponse)
                .toList();
    }

    // Danh sách lượt mượn của chính người dùng đang đăng nhập.
    @Transactional(readOnly = true)
    public List<MyBorrowRecordResponse> getMyBorrowRecords() {
        User user = getCurrentUser();

        return borrowRecordRepository.findByUserId(user.getId())
                .stream()
                .map(myBorrowRecordMapper::toMyBorrowRecordResponse)
                .toList();
    }

    // ==================== HELPER ====================

    private String toLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmed = keyword.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped + "%";
    }

    private int limitOf(User user) {
        return user.getMaxBorrowLimit() != null
                ? user.getMaxBorrowLimit()
                : DEFAULT_BORROW_LIMIT;
    }

    // Không tự động khôi phục sách đã DISCONTINUED.
    private void applyComputedStatus(Book book) {
        if (book.getStatus() == BookStatus.DISCONTINUED) {
            return;
        }

        book.setStatus(
                book.getAvailableCopies() > 0
                        ? BookStatus.AVAILABLE
                        : BookStatus.OUT_OF_STOCK
        );
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new AppException(ErrorCode.USERS_NOT_EXISTED)
                );
    }
}