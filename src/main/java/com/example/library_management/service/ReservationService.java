package com.example.library_management.service;

import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.Reservation;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.ReservationMapper;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class ReservationService {

    // Cung han muc voi BorrowRecordService: toi da 5 "suat sach" (dang muon + dang giu cho/xep hang) / user
    private static final int MAX_BORROW_LIMIT = 5;
    private static final int HOLD_DAYS = 2;

    private static final Set<ReservationStatus> ACTIVE_STATUSES = Set.of(
            ReservationStatus.PENDING, ReservationStatus.ACCEPTED);

    ReservationRepository reservationRepository;
    BorrowRecordRepository borrowRecordRepository;
    BookRepository bookRepository;
    UserRepository userRepository;
    FineRepository fineRepository;
    ReservationMapper reservationMapper;

    // MEMBER dat sach (online)
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        User user = getCurrentUser();

        if (fineRepository.existsByUserIdAndStatus(user.getId(), FineStatus.UNPAID)) {
            throw new AppException(ErrorCode.USER_HAS_UNPAID_FINE);
        }

        // pessimistic lock tren book de tranh race condition giua nhieu nguoi dat cung luc
        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        if (book.getStatus() == BookStatus.DISCONTINUED) {
            throw new AppException(ErrorCode.BOOK_DISCONTINUED);
        }

        if (reservationRepository.existsByUserIdAndBookIdAndStatusIn(user.getId(), book.getId(), ACTIVE_STATUSES)) {
            throw new AppException(ErrorCode.RESERVATION_DUPLICATED);
        }

        long currentBorrowing = borrowRecordRepository.countByUserIdAndStatus(user.getId(), BorrowStatus.BORROWED);
        long currentReserving = reservationRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES);
        if (currentBorrowing + currentReserving >= MAX_BORROW_LIMIT) {
            throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .book(book)
                .reservationDate(LocalDate.now())
                .build();

        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            // Con sach -> giu ngay (ACCEPTED tu dong), khong can librarian duyet
            acceptAndHold(reservation, book, null);
        } else {
            // Het sach -> xep hang cho (PENDING), khong tru availableCopies
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setExpiryDate(null);
        }

        reservation = reservationRepository.save(reservation);
        return enrichQueuePosition(reservationMapper.toReservationResponse(reservation), reservation);
    }

    // MEMBER tu huy dat sach - chi khi con PENDING (chua giu sach, khong anh huong availableCopies)
    @Transactional
    public ReservationResponse cancelMyReservation(Long id) {
        User user = getCurrentUser();
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_EXISTED));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new AppException(ErrorCode.RESERVATION_NOT_PENDING);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservationMapper.toReservationResponse(reservationRepository.save(reservation));
    }

    // LIBRARIAN/ADMIN huy ho. Neu dang ACCEPTED thi tra lai suat giu + day hang doi.
    @Transactional
    public ReservationResponse cancelReservationByStaff(Long id) {
        User staff = getCurrentUser();
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_EXISTED));

        if (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.ACCEPTED) {
            throw new AppException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }

        boolean wasAccepted = reservation.getStatus() == ReservationStatus.ACCEPTED;
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setProcessedBy(staff);
        reservationRepository.save(reservation);

        if (wasAccepted) {
            releaseAndPromote(reservation.getBook().getId());
        }

        return reservationMapper.toReservationResponse(reservation);
    }

    // LIBRARIAN/ADMIN duyet thu cong 1 reservation dang PENDING - phai la nguoi dau hang doi
//    @Transactional
//    public ReservationResponse acceptReservationManually(Long id) {
//        User staff = getCurrentUser();
//        Reservation reservation = reservationRepository.findById(id)
//                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_EXISTED));
//
//        if (reservation.getStatus() != ReservationStatus.PENDING) {
//            throw new AppException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
//        }
//
//        Book book = bookRepository.findByIdForUpdate(reservation.getBook().getId())
//                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
//
//        List<Reservation> queue = reservationRepository.findQueueForUpdate(book.getId(), ReservationStatus.PENDING);
//        if (queue.isEmpty() || !queue.get(0).getId().equals(reservation.getId())) {
//            throw new AppException(ErrorCode.RESERVATION_NOT_FIRST_IN_QUEUE);
//        }
//
//        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
//            throw new AppException(ErrorCode.BOOK_NOT_AVAILABLE);
//        }
//
//        acceptAndHold(reservation, book, staff);
//        return reservationMapper.toReservationResponse(reservationRepository.save(reservation));
//    }

    // Job dinh ky: quet reservation ACCEPTED da qua expiryDate ma chua den lay -> EXPIRED, roi tu dong day hang doi
    @Transactional
    public void processExpiredReservations() {
        LocalDate today = LocalDate.now();
        List<Reservation> expiredList = reservationRepository
                .findByStatusAndExpiryDateBefore(ReservationStatus.ACCEPTED, today);

        for (Reservation reservation : expiredList) {
            // load lai tung cai trong transaction rieng logic, tranh 1 loi lam hong ca batch thi van tiep tuc duoc it nhat trong cung transaction
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            releaseAndPromote(reservation.getBook().getId());
        }
    }

    // Giai phong 1 suat sach (vi 1 ACCEPTED vua bi huy/het han) va tu dong day nguoi dau PENDING queue len ACCEPTED
    @Transactional
    public void releaseAndPromote(Long bookId) {
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        book.setAvailableCopies(book.getAvailableCopies() + 1);

        List<Reservation> queue = reservationRepository.findQueueForUpdate(bookId, ReservationStatus.PENDING);
        if (!queue.isEmpty()) {
            Reservation next = queue.get(0);
            acceptAndHold(next, book, null);
            reservationRepository.save(next);
        } else {
            applyComputedStatus(book);
        }
        bookRepository.save(book);
    }

    // Dat 1 reservation thanh ACCEPTED va tru availableCopies cua book tuong ung (book da duoc lock tu truoc)
    private void acceptAndHold(Reservation reservation, Book book, User processedBy) {
        reservation.setStatus(ReservationStatus.ACCEPTED);
        reservation.setExpiryDate(LocalDate.now().plusDays(HOLD_DAYS));
        reservation.setProcessedBy(processedBy);

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        applyComputedStatus(book);
        bookRepository.save(book);
    }

    // Khong tu dong "hoi sinh" mot sach da DISCONTINUED chi vi so luong thay doi
    private void applyComputedStatus(Book book) {
        if (book.getStatus() == BookStatus.DISCONTINUED) {
            return;
        }
        book.setStatus(book.getAvailableCopies() > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations() {
        User user = getCurrentUser();
        return reservationRepository.findByUserId(user.getId())
                .stream()
                .map(r -> enrichQueuePosition(reservationMapper.toReservationResponse(r), r))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll()
                .stream()
                .map(r -> enrichQueuePosition(reservationMapper.toReservationResponse(r), r))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByBook(Long bookId) {
        return reservationRepository.findByBookId(bookId)
                .stream()
                .map(r -> enrichQueuePosition(reservationMapper.toReservationResponse(r), r))
                .toList();
    }

    // Tinh vi tri trong hang doi PENDING cua 1 reservation (1 = nguoi ke tiep se duoc len ACCEPTED).
    // Chi ap dung khi status = PENDING, cac trang thai khac tra ve null vi khong con xep hang nua.
    private ReservationResponse enrichQueuePosition(ReservationResponse response, Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            return response;
        }
        List<Reservation> queue = reservationRepository
                .findByBookIdAndStatusOrderByCreatedAtAscIdAsc(reservation.getBook().getId(), ReservationStatus.PENDING);
        int position = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(reservation.getId())) {
                position = i + 1;
                break;
            }
        }
        response.setQueuePosition(position > 0 ? position : null);
        return response;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}
