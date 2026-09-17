package com.example.library_management.service;

import com.example.library_management.dto.request.BorrowRecordRequest;
import com.example.library_management.dto.request.BorrowReturnRequest;
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

    // 7 ngay muon mac dinh, toi da 5 "suat sach" (dang muon + dang dat/giu cho)/user, 5.000 VND/ngay tre
    private static final int BORROW_DAYS = 7;
    private static final int MAX_BORROW_LIMIT = 5;
    private static final BigDecimal FINE_PER_DAY = new BigDecimal("5000");
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

        // chan muon neu da dat gioi han so "suat sach" (dang muon + dang dat/giu cho o Reservation)
        long currentBorrowing = borrowRecordRepository.countByUserIdAndStatus(user.getId(), BorrowStatus.BORROWED);
        long currentReserving = reservationRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE_RESERVATION_STATUSES);
        if (currentBorrowing + currentReserving >= MAX_BORROW_LIMIT) {
            throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
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
            // Muon truc tiep, khong thong qua reservation cua chinh minh:
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
    @Transactional
    public BorrowRecordResponse returnBook(Long borrowRecordId, BorrowReturnRequest request) {
        User processedBy = getCurrentUser();

        BorrowRecord borrowRecord = borrowRecordRepository.findById(borrowRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

        if (borrowRecord.getStatus() == BorrowStatus.RETURNED) {
            throw new AppException(ErrorCode.BORROW_ALREADY_RETURNED);
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

        // tra tre han -> tu dong tao Fine 5.000 VND/ngay tre
        if (today.isAfter(borrowRecord.getDueDate())) {
            long lateDays = ChronoUnit.DAYS.between(borrowRecord.getDueDate(), today);
            BigDecimal amount = FINE_PER_DAY.multiply(BigDecimal.valueOf(lateDays));

            Fine fine = Fine.builder()
                    .user(borrowRecord.getUser())
                    .borrowRecord(borrowRecord)
                    .amount(amount)
                    .reason(FineReason.OVERDUE)
                    .status(FineStatus.UNPAID)
                    .issuedDate(today)
                    .note("Tre han " + lateDays + " ngay")
                    .build();
            borrowRecord.setFine(fine);
        }

        return borrowRecordMapper.toBorrowRecordResponse(borrowRecordRepository.save(borrowRecord));
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
