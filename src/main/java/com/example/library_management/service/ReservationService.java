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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class ReservationService {

    // Cung han muc voi BorrowRecordService: toi da 5 "suat sach" / user, gom
    // BORROWED + OVERDUE + LOST (chua xu ly xong fine) + reservation dang giu cho/xep hang
    private static final int DEFAULT_BORROW_LIMIT = 5;
    private static final int HOLD_DAYS = 2;
    // PENDING da xep hang qua so ngay nay ma member VAN khong du dieu kien nhan sach -> tu dong EXPIRED
    private static final int STALE_PENDING_DAYS = 14;

    private static final Set<ReservationStatus> ACTIVE_STATUSES = Set.of(
            ReservationStatus.PENDING, ReservationStatus.ACCEPTED);

    ReservationRepository reservationRepository;
    BorrowRecordRepository borrowRecordRepository;
    BookRepository bookRepository;
    UserRepository userRepository;
    FineRepository fineRepository;
    ReservationMapper reservationMapper;

    // MEMBER dat sach (online) - day la CACH DUY NHAT de member co sach: con sach thi giu cho (ACCEPTED), het sach thi xep hang (PENDING).
    // Sau khi duoc giu cho, thu thu xac nhan giao sach (BorrowRecordService.handoverBook) thi moi tao BorrowRecord.
    // READ_COMMITTED: moi cau lenh doc du lieu moi nhat da commit (MySQL mac dinh REPEATABLE READ se doc "snapshot" cu).
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse createReservation(ReservationRequest request) {
        // Khoa dong user NGAY DAU: 2 request dat sach dong thoi cua cung 1 member bi xep hang, nen han muc khong the bi vuot
        User user = lockCurrentUser();

        if (fineRepository.existsByUserIdAndStatus(user.getId(), FineStatus.UNPAID)) {
            throw new AppException(ErrorCode.USER_HAS_UNPAID_FINE);
        }

        // tinh theo dueDate, khong doi scheduler danh dau OVERDUE
        if (borrowRecordRepository.existsByUserIdAndStatusInAndDueDateBefore(
                user.getId(), List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE), LocalDate.now())) {
            throw new AppException(ErrorCode.USER_HAS_OVERDUE_BOOK);
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

        // han muc: dang muon + qua han + mat chua den bu + dang giu cho + dang xep hang <= han muc (mac dinh 5)
        if (slotsInUse(user) >= limitOf(user)) {
            throw new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED);
        }

        // Day nhung nguoi PENDING da du dieu kien len truoc, de nguoi moi khong chen ngang hang doi
        promoteFromQueue(book);

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

    // MEMBER tu huy dat sach: PENDING (roi hang doi) hoac ACCEPTED (tra lai suat giu cho nguoi ke tiep)
    @Transactional
    public ReservationResponse cancelMyReservation(Long id) {
        User user = getCurrentUser();
        Reservation reservation = lockReservation(id);

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return cancelActiveReservation(reservation, null);
    }

    // LIBRARIAN/ADMIN huy ho. Neu dang ACCEPTED thi tra lai suat giu + day hang doi.
    @Transactional
    public ReservationResponse cancelReservationByStaff(Long id) {
        User staff = getCurrentUser();
        Reservation reservation = lockReservation(id);
        return cancelActiveReservation(reservation, staff);
    }

    // Reservation da duoc khoa (lockReservation). Chi huy duoc PENDING/ACCEPTED; ACCEPTED -> giai phong suat giu
    private ReservationResponse cancelActiveReservation(Reservation reservation, User staff) {
        if (!ACTIVE_STATUSES.contains(reservation.getStatus())) {
            throw new AppException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }

        boolean wasAccepted = reservation.getStatus() == ReservationStatus.ACCEPTED;
        reservation.setStatus(ReservationStatus.CANCELLED);
        if (staff != null) {
            reservation.setProcessedBy(staff);
        }
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

    // Job dinh ky: quet reservation ACCEPTED da qua expiryDate ma chua den lay -> EXPIRED, roi tu dong day hang doi.
    // Tung reservation duoc khoa va doc lai trang thai: neu thu thu vua giao sach / huy trong luc job chay thi bo qua.
    @Transactional
    public void processExpiredReservations() {
        LocalDate today = LocalDate.now();
        List<Long> expiredIds = reservationRepository
                .findIdsByStatusAndExpiryDateBefore(ReservationStatus.ACCEPTED, today);

        for (Long id : expiredIds) {
            Reservation reservation = lockReservation(id);
            if (reservation.getStatus() != ReservationStatus.ACCEPTED
                    || reservation.getExpiryDate() == null
                    || !reservation.getExpiryDate().isBefore(today)) {
                continue;
            }
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            releaseAndPromote(reservation.getBook().getId());
        }
    }

    // Goi khi user vua het vuong ban (tra / mien phat): xet lai hang doi cua moi sach ma user dang PENDING.
    // Truoc day nguoi nay bi bo qua (canHold = false) nen sach co the dang ranh ma khong ai kich hoat lai.
    // Duyet theo id sach tang dan, moi sach khoa rieng, de cac giao dich dong thoi khoa Book cung 1 thu tu.
    @Transactional
    public void promoteQueuesForUser(String userId) {
        for (Long bookId : reservationRepository.findBookIdsByUserIdAndStatus(userId, ReservationStatus.PENDING)) {
            promotePendingQueue(bookId);
        }
    }

    // Job dinh ky: PENDING xep hang qua STALE_PENDING_DAYS ngay ma member van khong du dieu kien (con phat / qua han /
    // vuot han muc) -> EXPIRED de giai phong slot cua ho. Member du dieu kien chi la dang cho het sach thi KHONG bi dung toi.
    @Transactional
    public int expireStalePendingReservations() {
        LocalDateTime before = LocalDateTime.now().minusDays(STALE_PENDING_DAYS);
        int expired = 0;
        for (Long id : reservationRepository.findIdsByStatusAndCreatedAtBefore(ReservationStatus.PENDING, before)) {
            Reservation reservation = lockReservation(id);
            // doc lai trong lock: co the vua duoc day len ACCEPTED / bi huy, hoac member vua het vuong ban
            if (reservation.getStatus() != ReservationStatus.PENDING || canHold(reservation.getUser())) {
                continue;
            }
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            expired++;
        }
        return expired;
    }

    // Co them ban sach (BookService tang totalCopies) -> day hang doi PENDING len ACCEPTED neu con suat
    @Transactional
    public void promotePendingQueue(Long bookId) {
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        promoteFromQueue(book);
    }

    // Giai phong 1 suat sach (vi 1 ban vua duoc tra / 1 ACCEPTED vua bi huy hoac het han) va day hang doi
    @Transactional
    public void releaseAndPromote(Long bookId) {
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        book.setAvailableCopies(book.getAvailableCopies() + 1);
        promoteFromQueue(book);
    }

    // QUY TAC HANG DOI: duyet PENDING theo thu tu dat, trong khi con sach thi giu cho nguoi DU DIEU KIEN (canHold).
    // Nguoi chua du dieu kien bi bo qua (van giu nguyen thu tu, van PENDING) de khong khoa sach vo ich 2 ngay.
    // Book phai duoc khoa truoc khi goi.
    private void promoteFromQueue(Book book) {
        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            for (Reservation next : reservationRepository.findQueueForUpdate(book.getId(), ReservationStatus.PENDING)) {
                if (book.getAvailableCopies() <= 0) {
                    break;
                }
                if (!canHold(next.getUser())) {
                    continue;
                }
                acceptAndHold(next, book, null);
                reservationRepository.save(next);
            }
        }
        applyComputedStatus(book);
        bookRepository.save(book);
    }

    // PENDING van dang chiem 1 slot tu luc dat nen khi len ACCEPTED tong slot khong tang; chi can kiem tra
    // user khong con no phat / sach qua han va tong slot khong vuot han muc (vi du admin vua ha han muc).
    private boolean canHold(User user) {
        String userId = user.getId();
        if (fineRepository.existsByUserIdAndStatus(userId, FineStatus.UNPAID)) {
            return false;
        }
        if (borrowRecordRepository.existsByUserIdAndStatusInAndDueDateBefore(
                userId, List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE), LocalDate.now())) {
            return false;
        }
        return slotsInUse(user) <= limitOf(user);
    }

    // Slot dang chiem = BORROWED + OVERDUE + LOST (con fine UNPAID) + reservation PENDING/ACCEPTED.
    // RETURNED / CANCELLED / EXPIRED / FULFILLED khong dem -> tra sach, huy, het han deu tu giai phong slot.
    private long slotsInUse(User user) {
        return borrowRecordRepository.countBorrowSlotsInUse(user.getId())
                + reservationRepository.countByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES);
    }

    private int limitOf(User user) {
        return user.getMaxBorrowLimit() != null ? user.getMaxBorrowLimit() : DEFAULT_BORROW_LIMIT;
    }

    // Khoa theo thu tu Book -> Reservation (cung thu tu voi promoteFromQueue) de tranh deadlock,
    // roi doc lai Reservation SAU khi da khoa: trang thai tra ve la trang thai moi nhat (giao sach / huy / het han khong de len nhau).
    @Transactional
    public Reservation lockReservation(Long id) {
        Long bookId = reservationRepository.findBookIdById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_EXISTED));
        bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_EXISTED));
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
        return toResponses(reservationRepository.findByUserId(user.getId()));
    }

    // Phan trang (LIBRARIAN/ADMIN)
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAllReservations(Pageable pageable) {
        return toPage(reservationRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getReservationsByBook(Long bookId, Pageable pageable) {
        return toPage(reservationRepository.findByBookId(bookId, pageable));
    }

    private List<ReservationResponse> toResponses(List<Reservation> reservations) {
        Map<Long, Integer> positions = queuePositions(reservations);
        return reservations.stream().map(r -> toResponse(r, positions)).toList();
    }

    private Page<ReservationResponse> toPage(Page<Reservation> page) {
        Map<Long, Integer> positions = queuePositions(page.getContent());
        return page.map(r -> toResponse(r, positions));
    }

    private ReservationResponse toResponse(Reservation reservation, Map<Long, Integer> positions) {
        ReservationResponse response = reservationMapper.toReservationResponse(reservation);
        response.setQueuePosition(positions.get(reservation.getId()));
        return response;
    }

    // Vi tri hang doi (1 = ke tiep) cho moi reservation PENDING trong danh sach: dung 1 query chung cho tat ca sach
    // thay vi 1 query / reservation. Reservation khong PENDING khong co trong map -> queuePosition = null.
    private Map<Long, Integer> queuePositions(Collection<Reservation> reservations) {
        Set<Long> bookIds = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .map(r -> r.getBook().getId())
                .collect(Collectors.toSet());
        if (bookIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> positions = new HashMap<>();
        Map<Long, Integer> countPerBook = new HashMap<>();
        for (Object[] row : reservationRepository.findQueueRows(bookIds, ReservationStatus.PENDING)) {
            Long bookId = (Long) row[0];
            Long reservationId = (Long) row[1];
            positions.put(reservationId, countPerBook.merge(bookId, 1, Integer::sum));
        }
        return positions;
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

    private User lockCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}
