package com.example.library_management.service;

import com.example.library_management.dto.request.BorrowRecordRequest;
import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.request.MarkLostRequest;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.Reservation;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.BorrowRecordMapper;
import com.example.library_management.mapper.MyBorrowRecordMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class BorrowRecordService {

    // 7 ngay muon mac dinh, toi da 5 "suat sach" (dang muon + qua han + mat chua xu ly xong + dang dat/giu cho)/user,
    // 5.000 VND/ngay tre (tran 30 ngay = 150.000 VND)
    private static final int BORROW_DAYS = 7;
    private static final int DEFAULT_BORROW_LIMIT = 5;
    private static final BigDecimal FINE_PER_DAY = new BigDecimal("5000");
    private static final int MAX_OVERDUE_FINE_DAYS = 30;

    // Qua han tu ngay thu 30 tro di ma chua tra -> tu dong coi la mat sach
    private static final int AUTO_LOST_AFTER_DAYS = 30;
    // Tien den = gia sach + phi xu ly. Sach chua nhap gia thi dung muc mac dinh (thu thu co the nhap lostAmount de ghi de)
    private static final BigDecimal LOST_PROCESSING_FEE = new BigDecimal("20000");
    private static final BigDecimal DEFAULT_REPLACEMENT_COST = new BigDecimal("100000");

    private static final Set<ReservationStatus> ACTIVE_RESERVATION_STATUSES = Set.of(
            ReservationStatus.PENDING, ReservationStatus.ACCEPTED);

    BorrowRecordRepository borrowRecordRepository;
    BookRepository bookRepository;
    UserRepository userRepository;
    FineRepository fineRepository;
    ReservationRepository reservationRepository;
    ReservationService reservationService;
    BorrowRecordMapper borrowRecordMapper;
    MyBorrowRecordMapper myBorrowRecordMapper;

    // MUON SACH - userId lay tu token
    @Transactional
    public BorrowRecordResponse borrowBook(BorrowRecordRequest request) {
        User user = getCurrentUser();

        // chan muon neu con phi phat chua thanh toan
        if (fineRepository.existsByUserIdAndStatus(user.getId(), FineStatus.UNPAID)) {
            throw new AppException(ErrorCode.USER_HAS_UNPAID_FINE);
        }

        // chan muon neu con sach qua han chua tra
        if (borrowRecordRepository.existsByUserIdAndStatus(user.getId(), BorrowStatus.OVERDUE)) {
            throw new AppException(ErrorCode.USER_HAS_OVERDUE_BOOK);
        }

        // pessimistic lock
        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        if (book.getStatus() == BookStatus.DISCONTINUED) {
            throw new AppException(ErrorCode.BOOK_DISCONTINUED);
        }

        // Neu user dang co reservation cho dung sach nay:
        //  - PENDING (chua toi luot)  -> chan muon truc tiep
        //  - ACCEPTED (da duoc giu)   -> cho muon, dung de "nhan sach da dat", KHONG tru availableCopies lan nua
        Optional<Reservation> myPending = reservationRepository
                .findByUserIdAndBookIdAndStatus(user.getId(), book.getId(), ReservationStatus.PENDING);
        if (myPending.isPresent()) {
            throw new AppException(ErrorCode.RESERVATION_NOT_ACCEPTED);
        }

        Optional<Reservation> myAccepted = reservationRepository
                .findByUserIdAndBookIdAndStatus(user.getId(), book.getId(), ReservationStatus.ACCEPTED);

        if (myAccepted.isEmpty()) {
            // Muon truc tiep: them 1 slot MOI -> phai kiem tra han muc.
            // slot dang dung = BORROWED + OVERDUE + LOST (con fine UNPAID) + reservation PENDING/ACCEPTED
            // (Nhan sach da giu thi khong kiem tra: RESERVATION -1, BORROWED +1, tong slot khong doi)
            long currentSlots = borrowRecordRepository.countBorrowSlotsInUse(user.getId())
                    + reservationRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE_RESERVATION_STATUSES);
            if (currentSlots >= limitOf(user)) {
                throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
            }

            // chi duoc muon khi con sach VA khong co ai dang xep hang (PENDING) cho sach nay
            if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
                throw new AppException(ErrorCode.BOOK_NOT_AVAILABLE);
            }
            if (reservationRepository.existsByBookIdAndStatus(book.getId(), ReservationStatus.PENDING)) {
                throw new AppException(ErrorCode.RESERVATION_HAS_ACTIVE_PENDING);
            }

            book.setAvailableCopies(book.getAvailableCopies() - 1);
            book.setStatus(book.getAvailableCopies() > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
            bookRepository.save(book);
        }
        // Neu co myAccepted: suat sach da bi tru tu luc reservation duoc ACCEPT, khong tru them o day

        LocalDate today = LocalDate.now();
        BorrowRecord borrowRecord = BorrowRecord.builder()
                .user(user)
                .book(book)
                .borrowDate(today)
                .dueDate(today.plusDays(BORROW_DAYS))
                .build();

        myAccepted.ifPresent(reservation -> {
            reservation.setStatus(ReservationStatus.FULFILLED);
            reservationRepository.save(reservation);
            borrowRecord.setReservation(reservation);
        });

        return borrowRecordMapper.toBorrowRecordResponse(borrowRecordRepository.save(borrowRecord));
    }

    // TRA SACH - chi LIBRARIAN/ADMIN xac nhan (permission borrow:return)
    // Record da LOST la trang thai cuoi: khong tra lai duoc (khong co luong "tim lai sach")
    @Transactional
    public BorrowRecordResponse returnBook(Long borrowRecordId, BorrowReturnRequest request) {
        User processedBy = getCurrentUser();

        // khoa record truoc, roi moi khoa book (cung thu tu voi markLost / autoMarkLost)
        BorrowRecord borrowRecord = borrowRecordRepository.findByIdForUpdate(borrowRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

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
        if (request != null && StringUtils.hasText(request.getNote())) {
            borrowRecord.setNote(request.getNote());
        }

        // tra lai 1 ban sach vao kho: uy quyen cho ReservationService (co lock rieng) de vua +1 availableCopies
        // vua tu dong day nguoi dau hang doi PENDING len ACCEPTED neu co. Ham nay tu bao toan trang thai
        // DISCONTINUED, khong tu y "hoi sinh" sach da ngung luu hanh.
        reservationService.releaseAndPromote(borrowRecord.getBook().getId());

        // tra tre han -> tu dong tao Fine 5.000 VND/ngay tre (toi da 30 ngay)
        createOverdueFine(borrowRecord, today);

        return borrowRecordMapper.toBorrowRecordResponse(borrowRecordRepository.save(borrowRecord));
    }

    // BAO MAT SACH - LIBRARIAN/ADMIN (permission borrow:return)
    @Transactional
    public BorrowRecordResponse markLost(Long borrowRecordId, MarkLostRequest request) {
        User staff = getCurrentUser();

        BorrowRecord borrowRecord = borrowRecordRepository.findByIdForUpdate(borrowRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

        String note = request != null ? request.getNote() : null;
        BigDecimal lostAmount = request != null ? request.getLostAmount() : null;

        return borrowRecordMapper.toBorrowRecordResponse(
                applyLost(borrowRecord, staff, note, lostAmount, false));
    }

    // ===================== JOB TU DONG (goi tu BorrowScheduler) =====================

    // BORROWED da qua han -> OVERDUE
    @Transactional
    public int markOverdueRecords() {
        List<BorrowRecord> records = borrowRecordRepository
                .findByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now());
        records.forEach(record -> record.setStatus(BorrowStatus.OVERDUE));
        borrowRecordRepository.saveAll(records);
        return records.size();
    }

    // Id cac OVERDUE da qua AUTO_LOST_AFTER_DAYS ngay. Scheduler goi autoMarkLost(id) tung cai
    // de moi record 1 transaction rieng, 1 record loi khong lam hong ca batch.
    @Transactional(readOnly = true)
    public List<Long> findLongOverdueRecordIds() {
        LocalDate threshold = LocalDate.now().minusDays(AUTO_LOST_AFTER_DAYS);
        return borrowRecordRepository
                .findByStatusAndDueDateLessThanEqual(BorrowStatus.OVERDUE, threshold)
                .stream()
                .map(BorrowRecord::getId)
                .toList();
    }

    @Transactional
    public void autoMarkLost(Long borrowRecordId) {
        BorrowRecord borrowRecord = borrowRecordRepository.findByIdForUpdate(borrowRecordId).orElse(null);
        // Da bi thu thu tra / bao mat truoc khi job toi luot -> bo qua
        if (borrowRecord == null || borrowRecord.getStatus() != BorrowStatus.OVERDUE) {
            return;
        }
        applyLost(borrowRecord, null,
                "Tu dong: qua han " + AUTO_LOST_AFTER_DAYS + " ngay chua tra sach", null, true);
    }

    // ===================== LOGIC DUNG CHUNG =====================

    // Danh dau LOST. Goi khi da giu lock cua borrowRecord.
    //  - tao Fine OVERDUE (neu tre) + Fine LOST_BOOK (cong don)
    //  - totalCopies -= 1, KHONG cong availableCopies, KHONG releaseAndPromote (khong co ban nao ve kho)
    //  - record van chiem slot trong han muc muon cho toi khi het Fine UNPAID (xem countSlotsInUse)
    //  - LOST la trang thai cuoi, khong co buoc "tim lai sach"
    private BorrowRecord applyLost(BorrowRecord borrowRecord, User processedBy, String note,
                                   BigDecimal lostAmountOverride, boolean auto) {
        if (borrowRecord.getStatus() == BorrowStatus.LOST) {
            throw new AppException(ErrorCode.BORROW_ALREADY_LOST);
        }
        if (borrowRecord.getStatus() != BorrowStatus.BORROWED
                && borrowRecord.getStatus() != BorrowStatus.OVERDUE) {
            throw new AppException(ErrorCode.BORROW_CANNOT_MARK_LOST);
        }

        LocalDate today = LocalDate.now();

        // khoa book sau khi da khoa record
        Book book = bookRepository.findByIdForUpdate(borrowRecord.getBook().getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        borrowRecord.setStatus(BorrowStatus.LOST);
        borrowRecord.setLostDate(today);
        borrowRecord.setProcessedBy(processedBy); // null neu he thong tu dong
        if (StringUtils.hasText(note)) {
            borrowRecord.setNote(note);
        }

        // 1) phi tre han tinh toi ngay mat (neu co)
        createOverdueFine(borrowRecord, today);

        // 2) tien den sach
        BigDecimal amount;
        String fineNote;
        if (lostAmountOverride != null) {
            amount = lostAmountOverride;
            fineNote = "Mat sach: so tien do thu thu dinh gia";
        } else {
            BigDecimal price = book.getPrice() != null ? book.getPrice() : DEFAULT_REPLACEMENT_COST;
            amount = price.add(LOST_PROCESSING_FEE);
            fineNote = "Mat sach: gia sach " + price.toPlainString()
                    + (book.getPrice() == null ? " (mac dinh, chua nhap gia)" : "")
                    + " + phi xu ly " + LOST_PROCESSING_FEE.toPlainString();
        }
        if (auto) {
            fineNote = "Tu dong. " + fineNote;
        }
        saveFine(borrowRecord, FineReason.LOST_BOOK, amount, today, fineNote);

        // 3) kho: ban mat khong quay lai -> ghi giam tong so ban, giu nguyen availableCopies
        book.setTotalCopies(Math.max(0, book.getTotalCopies() - 1));
        applyComputedStatus(book);
        bookRepository.save(book);

        return borrowRecordRepository.save(borrowRecord);
    }

    // Tao Fine OVERDUE neu today > dueDate. So ngay tinh toi da MAX_OVERDUE_FINE_DAYS.
    private void createOverdueFine(BorrowRecord borrowRecord, LocalDate today) {
        if (!today.isAfter(borrowRecord.getDueDate())) {
            return;
        }
        long lateDays = ChronoUnit.DAYS.between(borrowRecord.getDueDate(), today);
        long chargedDays = Math.min(lateDays, MAX_OVERDUE_FINE_DAYS);
        BigDecimal amount = FINE_PER_DAY.multiply(BigDecimal.valueOf(chargedDays));

        String note = "Tre han " + lateDays + " ngay"
                + (lateDays > chargedDays ? " (tinh toi da " + MAX_OVERDUE_FINE_DAYS + " ngay)" : "");
        saveFine(borrowRecord, FineReason.OVERDUE, amount, today, note);
    }

    // Luu Fine bang persist ro rang (co id ngay) roi gan vao borrowRecord.fines
    private void saveFine(BorrowRecord borrowRecord, FineReason reason, BigDecimal amount,
                          LocalDate issuedDate, String note) {
        Fine fine = Fine.builder()
                .user(borrowRecord.getUser())
                .borrowRecord(borrowRecord)
                .amount(amount)
                .reason(reason)
                .status(FineStatus.UNPAID)
                .issuedDate(issuedDate)
                .note(truncate(note))
                .build();
        borrowRecord.getFines().add(fineRepository.save(fine));
    }

    // Khong tu dong "hoi sinh" mot sach da DISCONTINUED chi vi so luong thay doi
    private void applyComputedStatus(Book book) {
        if (book.getStatus() == BookStatus.DISCONTINUED) {
            return;
        }
        book.setStatus(book.getAvailableCopies() > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
    }

    private int limitOf(User user) {
        return user.getMaxBorrowLimit() != null ? user.getMaxBorrowLimit() : DEFAULT_BORROW_LIMIT;
    }

    private String truncate(String text) {
        return text != null && text.length() > 255 ? text.substring(0, 255) : text;
    }

    // DANH SACH TAT CA (LIBRARIAN/ADMIN - borrow:manage)
    @Transactional(readOnly = true)
    public List<BorrowRecordResponse> getAllBorrowRecords() {
        return borrowRecordRepository.findAll()
                .stream()
                .map(borrowRecordMapper::toBorrowRecordResponse)
                .toList();
    }

    // CHI TIET 1 BORROW RECORD (LIBRARIAN/ADMIN - borrow:manage)
    @Transactional(readOnly = true)
    public BorrowRecordResponse getBorrowRecordById(Long id) {
        BorrowRecord borrowRecord = borrowRecordRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));
        return borrowRecordMapper.toBorrowRecordResponse(borrowRecord);
    }

    // DANH SACH MUON CUA 1 USER CU THE (LIBRARIAN/ADMIN - borrow:manage)
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

    // DANH SACH MUON CUA CHINH MINH
    @Transactional(readOnly = true)
    public List<MyBorrowRecordResponse> getMyBorrowRecords() {
        User user = getCurrentUser();
        return borrowRecordRepository.findByUserId(user.getId())
                .stream()
                .map(myBorrowRecordMapper::toMyBorrowRecordResponse)
                .toList();
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}
