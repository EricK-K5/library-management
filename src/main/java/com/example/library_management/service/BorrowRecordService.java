package com.example.library_management.service;

import com.example.library_management.dto.request.BorrowRecordRequest;
import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.BorrowRecordMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class BorrowRecordService {

    // Quy dinh nghiep vu (theo yeu cau): 7 ngay muon mac dinh, toi da 5 sach/user, 5.000 VND/ngay tre
    private static final int BORROW_DAYS = 7;
    private static final int MAX_BORROW_LIMIT = 5;
    private static final BigDecimal FINE_PER_DAY = new BigDecimal("5000");

    BorrowRecordRepository borrowRecordRepository;
    BookRepository bookRepository;
    UserRepository userRepository;
    FineRepository fineRepository;
    BorrowRecordMapper borrowRecordMapper;

    // MUON SACH - MEMBER tu muon cho chinh minh, userId lay tu token
    @Transactional
    public BorrowRecordResponse borrowBook(BorrowRecordRequest request) {
        User user = getCurrentUser();

        // Chan muon neu con phi phat chua thanh toan
        if (fineRepository.existsByUserIdAndStatus(user.getId(), FineStatus.UNPAID)) {
            throw new AppException(ErrorCode.USER_HAS_UNPAID_FINE);
        }

        // Chan muon neu da dat gioi han so sach dang muon (co dinh 5, moi user nhu nhau)
        long currentBorrowing = borrowRecordRepository.countByUserIdAndStatus(user.getId(), BorrowStatus.BORROWED);
        if (currentBorrowing >= MAX_BORROW_LIMIT) {
            throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
        }

        // Pessimistic Lock: khoa row sach lai de tranh 2 user cung muon vuot qua so luong con lai
        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            throw new AppException(ErrorCode.BOOK_NOT_AVAILABLE);
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        book.setStatus(book.getAvailableCopies() > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
        bookRepository.save(book);

        LocalDate today = LocalDate.now();
        BorrowRecord borrowRecord = BorrowRecord.builder()
                .user(user)
                .book(book)
                .borrowDate(today)
                .dueDate(today.plusDays(BORROW_DAYS))
                .status(BorrowStatus.BORROWED)
                .build();

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

        // Tra lai 1 ban sach vao kho (co lock de tranh xung dot voi luot muon khac)
        Book book = bookRepository.findByIdForUpdate(borrowRecord.getBook().getId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        book.setStatus(BookStatus.AVAILABLE);
        bookRepository.save(book);

        // Tra tre han -> tu dong tao Fine 5.000 VND/ngay tre
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

    // DANH SACH MUON CUA CHINH MINH (MEMBER - borrow:read)
    @Transactional(readOnly = true)
    public List<BorrowRecordResponse> getMyBorrowRecords() {
        User user = getCurrentUser();
        return borrowRecordRepository.findByUserId(user.getId())
                .stream()
                .map(borrowRecordMapper::toBorrowRecordResponse)
                .toList();
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}
