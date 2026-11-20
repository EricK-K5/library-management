package com.example.library_management.service;

import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.Reservation;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.event.MailEvents;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.ReservationMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock BorrowRecordRepository borrowRecordRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock FineRepository fineRepository;
    @Mock ReservationMapper reservationMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    ReservationService service;

    final LocalDate today = LocalDate.now();

    User member;
    User staff;
    Book book;

    @BeforeEach
    void setUp() {
        // Thu tu tham so theo thu tu field trong ReservationService (@RequiredArgsConstructor)
        service = new ReservationService(reservationRepository, borrowRecordRepository, bookRepository,
                userRepository, fineRepository, reservationMapper, eventPublisher);

        member = User.builder().id("m1").username("john").fullName("John Doe")
                .email("john@example.com").maxBorrowLimit(5).build();
        staff = User.builder().id("s1").username("librarian").build();
        book = Book.builder().id(5L).title("Clean Code").totalCopies(3).availableCopies(2).build();

        authenticateAs("john");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // helper

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null));
    }

    private void stubMapper() {
        // Moi lan tra ve 1 response moi (service co the sua queuePosition tren do)
        when(reservationMapper.toReservationResponse(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            return ReservationResponse.builder().id(r.getId()).status(r.getStatus()).build();
        });
    }

    private Reservation reservation(Long id, User user, ReservationStatus status) {
        return Reservation.builder().id(id).user(user).book(book).status(status).build();
    }

    // lockReservation(id) = findBookIdById -> khoa book -> khoa reservation
    private void stubLock(Reservation reservation) {
        when(reservationRepository.findBookIdById(reservation.getId())).thenReturn(Optional.of(reservation.getBook().getId()));
        when(bookRepository.findByIdForUpdate(reservation.getBook().getId())).thenReturn(Optional.of(reservation.getBook()));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
    }

    private void stubSlots(User user, long borrowSlots, long reservationSlots) {
        when(borrowRecordRepository.countBorrowSlotsInUse(user.getId())).thenReturn(borrowSlots);
        when(reservationRepository.countByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(reservationSlots);
    }

    private Object publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

   // createReservation
    private final ReservationRequest request = ReservationRequest.builder().bookId(5L).build();

    private void stubCreatePrerequisites() {
        when(userRepository.findByUsernameForUpdate("john")).thenReturn(Optional.of(member));
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
    }

    private void assertCreateRejected(ErrorCode expected) {
        AppException ex = assertThrows(AppException.class, () -> service.createReservation(request));
        assertEquals(expected, ex.getErrorCode());
        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createReservation_copyAvailable_acceptsImmediatelyAndHoldsOneCopy() {
        stubCreatePrerequisites();
        stubMapper();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        ReservationResponse response = service.createReservation(request);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();
        assertEquals(ReservationStatus.ACCEPTED, saved.getStatus());   // con sach -> giu ngay, khong cho thu thu duyet
        assertEquals(today.plusDays(2), saved.getExpiryDate());         // giu cho 2 ngay
        assertNull(saved.getProcessedBy());                             // he thong tu duyet
        assertSame(member, saved.getUser());

        assertEquals(1, book.getAvailableCopies());                     // 2 -> 1
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
        assertNull(response.getQueuePosition());                        // khong xep hang

        MailEvents.ReservationAccept event = assertInstanceOf(MailEvents.ReservationAccept.class, publishedEvent());
        assertEquals("john@example.com", event.email());
        assertEquals("Clean Code", event.bookTitle());
        assertEquals(today.plusDays(2), event.expiryDate());
    }

    @Test
    void createReservation_lastCopy_bookBecomesOutOfStock() {
        book.setAvailableCopies(1);
        stubCreatePrerequisites();
        stubMapper();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createReservation(request);

        assertEquals(0, book.getAvailableCopies());
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
    }

    @Test
    void createReservation_noCopies_queuesAsPendingWithPositionAndMail() {
        book.setAvailableCopies(0);
        stubCreatePrerequisites();
        stubMapper();
        AtomicReference<Reservation> savedRef = new AtomicReference<>();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(10L);
            savedRef.set(r);
            return r;
        });
        // Da co 1 nguoi xep hang truoc -> reservation moi o vi tri thu 2
        Reservation ahead = reservation(3L, User.builder().id("w1").build(), ReservationStatus.PENDING);
        when(reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(5L, ReservationStatus.PENDING))
                .thenAnswer(inv -> List.of(ahead, savedRef.get()));

        ReservationResponse response = service.createReservation(request);

        Reservation saved = savedRef.get();
        assertEquals(ReservationStatus.PENDING, saved.getStatus());
        assertNull(saved.getExpiryDate());
        assertEquals(0, book.getAvailableCopies());                    // xep hang khong tru ban nao
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
        assertEquals(2, response.getQueuePosition());

        MailEvents.ReservationQueued event = assertInstanceOf(MailEvents.ReservationQueued.class, publishedEvent());
        assertEquals("john@example.com", event.email());
        assertEquals("Clean Code", event.bookTitle());
        assertEquals(2, event.queuePosition());
    }

    @Test
    void createReservation_oneSlotLeft_isStillAllowed() {
        stubCreatePrerequisites();
        stubMapper();
        stubSlots(member, 3, 1); // 4 < 5
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = service.createReservation(request);

        assertEquals(ReservationStatus.ACCEPTED, response.getStatus());
    }

    @Test
    void createReservation_waiterAheadGetsTheFreeCopyFirst_newcomerIsQueued() {
        // Co 1 ban ranh + 1 nguoi dang cho du dieu kien: nguoi cho duoc len truoc, nguoi moi khong chen ngang
        book.setAvailableCopies(1);
        stubCreatePrerequisites();
        stubMapper();
        Reservation waiting = reservation(3L, User.builder().id("w1").email("w1@example.com").fullName("W").build(),
                ReservationStatus.PENDING);
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(waiting));
        AtomicReference<Reservation> savedNew = new AtomicReference<>();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(10L);
                savedNew.set(r);
            }
            return r;
        });
        when(reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(5L, ReservationStatus.PENDING))
                .thenAnswer(inv -> List.of(savedNew.get()));

        service.createReservation(request);

        assertEquals(ReservationStatus.ACCEPTED, waiting.getStatus());
        assertEquals(ReservationStatus.PENDING, savedNew.get().getStatus());
        assertEquals(0, book.getAvailableCopies());
    }

    @Test
    void createReservation_userNotFound_throwsBeforeAnyOtherCheck() {
        when(userRepository.findByUsernameForUpdate("john")).thenReturn(Optional.empty());

        assertCreateRejected(ErrorCode.USERS_NOT_EXISTED);
        verifyNoInteractions(fineRepository, bookRepository);
    }

    @Test
    void createReservation_unpaidFine_throwsBeforeLockingBook() {
        when(userRepository.findByUsernameForUpdate("john")).thenReturn(Optional.of(member));
        when(fineRepository.existsByUserIdAndStatus("m1", FineStatus.UNPAID)).thenReturn(true);

        assertCreateRejected(ErrorCode.USER_HAS_UNPAID_FINE);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void createReservation_hasOverdueBook_throws() {
        when(userRepository.findByUsernameForUpdate("john")).thenReturn(Optional.of(member));
        when(borrowRecordRepository.existsByUserIdAndStatusInAndDueDateBefore(eq("m1"), any(), eq(today)))
                .thenReturn(true);

        assertCreateRejected(ErrorCode.USER_HAS_OVERDUE_BOOK);
        verifyNoInteractions(bookRepository);
    }

    @Test
    void createReservation_bookNotFound_throws() {
        when(userRepository.findByUsernameForUpdate("john")).thenReturn(Optional.of(member));
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        assertCreateRejected(ErrorCode.BOOK_NOT_EXISTED);
    }

    @Test
    void createReservation_discontinuedBook_throws() {
        book.setStatus(BookStatus.DISCONTINUED);
        stubCreatePrerequisites();

        assertCreateRejected(ErrorCode.BOOK_DISCONTINUED);
    }

    @Test
    void createReservation_alreadyHasActiveReservationForSameBook_throwsDuplicated() {
        stubCreatePrerequisites();
        when(reservationRepository.existsByUserIdAndBookIdAndStatusIn(eq("m1"), eq(5L), any())).thenReturn(true);

        assertCreateRejected(ErrorCode.RESERVATION_DUPLICATED);
    }

    @Test
    void createReservation_slotsReachLimit_throwsLimitExceeded() {
        stubCreatePrerequisites();
        stubSlots(member, 4, 1); // 5 >= 5 (da dung het han muc, khong dat them duoc)

        assertCreateRejected(ErrorCode.BORROW_LIMIT_EXCEEDED);
    }

   // cancelMyReservation / cancelReservationByStaff
    @Test
    void cancelMyReservation_pending_cancelsWithoutReleasingAnyCopy() {
        book.setAvailableCopies(0);
        Reservation pending = reservation(10L, member, ReservationStatus.PENDING);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        stubLock(pending);
        stubMapper();

        ReservationResponse response = service.cancelMyReservation(10L);

        assertEquals(ReservationStatus.CANCELLED, pending.getStatus());
        assertNull(pending.getProcessedBy());
        verify(reservationRepository).save(pending);
        assertEquals(0, book.getAvailableCopies());                 // PENDING chua giu ban nao -> khong tra lai
        verify(bookRepository, times(1)).findByIdForUpdate(5L);     // chi lan khoa trong lockReservation
        assertEquals(10L, response.getId());
    }

    @Test
    void cancelMyReservation_accepted_releasesCopyAndPromotesQueue() {
        book.setAvailableCopies(0);
        book.setStatus(BookStatus.OUT_OF_STOCK);
        Reservation accepted = reservation(10L, member, ReservationStatus.ACCEPTED);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        stubLock(accepted);
        stubMapper();

        service.cancelMyReservation(10L);

        assertEquals(ReservationStatus.CANCELLED, accepted.getStatus());
        assertEquals(1, book.getAvailableCopies());                 // tra lai suat giu
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
        verify(bookRepository, times(2)).findByIdForUpdate(5L);     // lockReservation + releaseAndPromote
    }

    @Test
    void cancelMyReservation_someoneElsesReservation_throwsUnauthorized() {
        User other = User.builder().id("m2").username("other").build();
        Reservation theirs = reservation(10L, other, ReservationStatus.PENDING);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        stubLock(theirs);

        AppException ex = assertThrows(AppException.class, () -> service.cancelMyReservation(10L));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals(ReservationStatus.PENDING, theirs.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"FULFILLED", "CANCELLED", "EXPIRED"})
    void cancelMyReservation_alreadyProcessed_throws(ReservationStatus status) {
        Reservation done = reservation(10L, member, status);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        stubLock(done);

        AppException ex = assertThrows(AppException.class, () -> service.cancelMyReservation(10L));

        assertEquals(ErrorCode.RESERVATION_ALREADY_PROCESSED, ex.getErrorCode());
        assertEquals(status, done.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void cancelMyReservation_notFound_throwsNotExisted() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(reservationRepository.findBookIdById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.cancelMyReservation(99L));

        assertEquals(ErrorCode.RESERVATION_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void cancelReservationByStaff_accepted_recordsStaffAndReleasesCopy() {
        authenticateAs("librarian");
        book.setAvailableCopies(0);
        Reservation accepted = reservation(10L, member, ReservationStatus.ACCEPTED); // cua member, thu thu huy ho
        when(userRepository.findByUsername("librarian")).thenReturn(Optional.of(staff));
        stubLock(accepted);
        stubMapper();

        service.cancelReservationByStaff(10L);

        assertEquals(ReservationStatus.CANCELLED, accepted.getStatus());
        assertSame(staff, accepted.getProcessedBy());               // ghi nhan thu thu xu ly
        assertEquals(1, book.getAvailableCopies());
    }

    @Test
    void cancelReservationByStaff_alreadyCancelled_throws() {
        authenticateAs("librarian");
        Reservation done = reservation(10L, member, ReservationStatus.CANCELLED);
        when(userRepository.findByUsername("librarian")).thenReturn(Optional.of(staff));
        stubLock(done);

        AppException ex = assertThrows(AppException.class, () -> service.cancelReservationByStaff(10L));

        assertEquals(ErrorCode.RESERVATION_ALREADY_PROCESSED, ex.getErrorCode());
    }

   // Job: het han giu cho / PENDING qua lau
    @Test
    void processExpiredReservations_expiredAccepted_becomesExpiredAndCopyReleased() {
        book.setAvailableCopies(0);
        Reservation expired = reservation(10L, member, ReservationStatus.ACCEPTED);
        expired.setExpiryDate(today.minusDays(1));
        when(reservationRepository.findIdsByStatusAndExpiryDateBefore(ReservationStatus.ACCEPTED, today))
                .thenReturn(List.of(10L));
        stubLock(expired);

        service.processExpiredReservations();

        assertEquals(ReservationStatus.EXPIRED, expired.getStatus());
        verify(reservationRepository).save(expired);
        assertEquals(1, book.getAvailableCopies());
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
    }

    @Test
    void processExpiredReservations_handedOverMeanwhile_isSkipped() {
        // Thu thu vua giao sach (FULFILLED) trong luc job chay: doc lai trong lock thay doi trang thai -> bo qua
        Reservation fulfilled = reservation(10L, member, ReservationStatus.FULFILLED);
        fulfilled.setExpiryDate(today.minusDays(1));
        when(reservationRepository.findIdsByStatusAndExpiryDateBefore(ReservationStatus.ACCEPTED, today))
                .thenReturn(List.of(10L));
        stubLock(fulfilled);

        service.processExpiredReservations();

        assertEquals(ReservationStatus.FULFILLED, fulfilled.getStatus());
        verify(reservationRepository, never()).save(any());
        verify(bookRepository, times(1)).findByIdForUpdate(5L); // khong release
    }

    @Test
    void processExpiredReservations_expiryDateNotBeforeToday_isSkipped() {
        Reservation stillValid = reservation(10L, member, ReservationStatus.ACCEPTED);
        stillValid.setExpiryDate(today); // het han dung hom nay -> van con hieu luc
        when(reservationRepository.findIdsByStatusAndExpiryDateBefore(ReservationStatus.ACCEPTED, today))
                .thenReturn(List.of(10L));
        stubLock(stillValid);

        service.processExpiredReservations();

        assertEquals(ReservationStatus.ACCEPTED, stillValid.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void expireStalePendingReservations_expiresOnlyThoseWhoStillCannotHold() {
        User blocked = User.builder().id("b1").build();
        User eligible = User.builder().id("e1").build();
        Reservation blockedPending = reservation(1L, blocked, ReservationStatus.PENDING);
        Reservation eligiblePending = reservation(2L, eligible, ReservationStatus.PENDING);
        Reservation promoted = reservation(3L, eligible, ReservationStatus.ACCEPTED);
        when(reservationRepository.findIdsByStatusAndCreatedAtBefore(eq(ReservationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        stubLock(blockedPending);
        stubLock(eligiblePending);
        stubLock(promoted);
        when(fineRepository.existsByUserIdAndStatus("b1", FineStatus.UNPAID)).thenReturn(true); // b1 van no phat

        int expired = service.expireStalePendingReservations();

        assertEquals(1, expired);
        assertEquals(ReservationStatus.EXPIRED, blockedPending.getStatus());   // van khong du dieu kien -> giai phong slot
        assertEquals(ReservationStatus.PENDING, eligiblePending.getStatus());  // du dieu kien, chi dang cho het sach -> giu
        assertEquals(ReservationStatus.ACCEPTED, promoted.getStatus());        // da duoc day len trong luc job chay -> bo qua

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reservationRepository).findIdsByStatusAndCreatedAtBefore(eq(ReservationStatus.PENDING), cutoff.capture());
        Duration diff = Duration.between(LocalDateTime.now().minusDays(14), cutoff.getValue()).abs();
        assertTrue(diff.getSeconds() < 5, "moc cat phai la khoang 14 ngay truoc");
    }

   // Day hang doi (promote)
    @Test
    void promotePendingQueue_bookNotFound_throws() {
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.promotePendingQueue(5L));

        assertEquals(ErrorCode.BOOK_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void promotePendingQueue_skipsIneligibleWaiterAndPromotesNextOne() {
        book.setAvailableCopies(1);
        User blocked = User.builder().id("a1").email("a@x.com").fullName("A").build();
        User ok = User.builder().id("b1").email("b@x.com").fullName("B").build();
        Reservation first = reservation(1L, blocked, ReservationStatus.PENDING);  // dung dau hang nhung dang no phat
        Reservation second = reservation(2L, ok, ReservationStatus.PENDING);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(first, second));
        when(fineRepository.existsByUserIdAndStatus("a1", FineStatus.UNPAID)).thenReturn(true);

        service.promotePendingQueue(5L);

        // Nguoi chua du dieu kien bi bo qua (giu nguyen thu tu), khong khoa sach vo ich 2 ngay
        assertEquals(ReservationStatus.PENDING, first.getStatus());
        assertEquals(ReservationStatus.ACCEPTED, second.getStatus());
        assertEquals(today.plusDays(2), second.getExpiryDate());
        assertEquals(0, book.getAvailableCopies());
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
    }

    @Test
    void promotePendingQueue_promotesOnlyAsManyAsFreeCopies() {
        book.setAvailableCopies(1);
        Reservation first = reservation(1L, User.builder().id("a1").email("a@x.com").fullName("A").build(), ReservationStatus.PENDING);
        Reservation second = reservation(2L, User.builder().id("b1").email("b@x.com").fullName("B").build(), ReservationStatus.PENDING);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(first, second));

        service.promotePendingQueue(5L);

        assertEquals(ReservationStatus.ACCEPTED, first.getStatus());   // dung dau hang duoc truoc
        assertEquals(ReservationStatus.PENDING, second.getStatus());   // het ban -> van cho
    }

    @Test
    void promotePendingQueue_slotLimitBoundary_overLimitSkippedAtLimitPromoted() {
        book.setAvailableCopies(1);
        // PENDING da chiem 1 slot tu luc dat nen "bang han muc" la hop le, chi "vuot" moi bi bo qua
        User over = User.builder().id("o1").email("o@x.com").fullName("O").maxBorrowLimit(2).build();
        User exact = User.builder().id("x1").email("x@x.com").fullName("X").maxBorrowLimit(2).build();
        stubSlots(over, 3, 0);   // 3 > 2
        stubSlots(exact, 2, 0);  // 2 <= 2
        Reservation overRes = reservation(1L, over, ReservationStatus.PENDING);
        Reservation exactRes = reservation(2L, exact, ReservationStatus.PENDING);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(overRes, exactRes));

        service.promotePendingQueue(5L);

        assertEquals(ReservationStatus.PENDING, overRes.getStatus());
        assertEquals(ReservationStatus.ACCEPTED, exactRes.getStatus());
    }

    @Test
    void promotePendingQueue_noFreeCopy_doesNotTouchQueueButRecomputesStatus() {
        book.setAvailableCopies(0);
        book.setStatus(BookStatus.AVAILABLE); // lech: het ban nhung status chua dung
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));

        service.promotePendingQueue(5L);

        verify(reservationRepository, never()).findQueueForUpdate(any(), any());
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
        verify(bookRepository).save(book);
    }

    @Test
    void releaseAndPromote_noQueue_increasesCopyAndMarksAvailable() {
        book.setAvailableCopies(0);
        book.setStatus(BookStatus.OUT_OF_STOCK);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));

        service.releaseAndPromote(5L);

        assertEquals(1, book.getAvailableCopies());
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
    }

    @Test
    void releaseAndPromote_withEligibleWaiter_copyGoesStraightToWaiter() {
        book.setAvailableCopies(0);
        User ok = User.builder().id("b1").email("b@x.com").fullName("B").build();
        Reservation waiting = reservation(2L, ok, ReservationStatus.PENDING);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(waiting));

        service.releaseAndPromote(5L);

        assertEquals(ReservationStatus.ACCEPTED, waiting.getStatus());
        assertEquals(0, book.getAvailableCopies());                    // +1 roi -1 cho nguoi dang cho
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
        assertInstanceOf(MailEvents.ReservationAccept.class, publishedEvent());
    }

    @Test
    void releaseAndPromote_discontinuedBook_statusIsNotRevived() {
        book.setAvailableCopies(0);
        book.setStatus(BookStatus.DISCONTINUED);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));

        service.releaseAndPromote(5L);

        assertEquals(1, book.getAvailableCopies());
        assertEquals(BookStatus.DISCONTINUED, book.getStatus());
    }

    @Test
    void releaseAndPromote_bookNotFound_throws() {
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.releaseAndPromote(5L));

        assertEquals(ErrorCode.BOOK_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void promoteQueuesForUser_checksEveryBookTheUserWaitsForInAscendingOrder() {
        Book other = Book.builder().id(7L).title("Dune").totalCopies(1).availableCopies(0).build();
        when(reservationRepository.findBookIdsByUserIdAndStatus("m1", ReservationStatus.PENDING))
                .thenReturn(List.of(5L, 7L));
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(bookRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(other));

        service.promoteQueuesForUser("m1");

        InOrder inOrder = inOrder(bookRepository);
        inOrder.verify(bookRepository).findByIdForUpdate(5L);  // khoa theo thu tu id tang dan de tranh deadlock
        inOrder.verify(bookRepository).findByIdForUpdate(7L);
    }

    @Test
    void promoteQueuesForUser_memberNowEligible_getsFreeCopy() {
        // Truoc do member bi bo qua (dang no phat); vua het vuong ban -> sach dang ranh phai duoc giu cho ho
        book.setAvailableCopies(1);
        Reservation waiting = reservation(2L, member, ReservationStatus.PENDING);
        when(reservationRepository.findBookIdsByUserIdAndStatus("m1", ReservationStatus.PENDING)).thenReturn(List.of(5L));
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
        when(reservationRepository.findQueueForUpdate(5L, ReservationStatus.PENDING)).thenReturn(List.of(waiting));

        service.promoteQueuesForUser("m1");

        assertEquals(ReservationStatus.ACCEPTED, waiting.getStatus());
        assertEquals(0, book.getAvailableCopies());
    }

   // Truy van
    @Test
    void getMyReservations_fillsQueuePositionOnlyForPendingOnes() {
        Reservation pending = reservation(10L, member, ReservationStatus.PENDING);
        Reservation accepted = reservation(11L, member, ReservationStatus.ACCEPTED);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(reservationRepository.findByUserId("m1")).thenReturn(List.of(pending, accepted));
        stubMapper();
        // Hang doi sach 5: reservation 3 dung truoc, reservation 10 (cua member) dung thu 2
        when(reservationRepository.findQueueRows(Set.of(5L), ReservationStatus.PENDING))
                .thenReturn(List.of(new Object[]{5L, 3L}, new Object[]{5L, 10L}));

        List<ReservationResponse> result = service.getMyReservations();

        assertEquals(2, result.get(0).getQueuePosition());   // PENDING -> vi tri 2
        assertNull(result.get(1).getQueuePosition());        // ACCEPTED -> khong xep hang
    }

    @Test
    void getMyReservations_noPending_doesNotQueryQueue() {
        Reservation accepted = reservation(11L, member, ReservationStatus.ACCEPTED);
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(reservationRepository.findByUserId("m1")).thenReturn(List.of(accepted));
        stubMapper();

        service.getMyReservations();

        verify(reservationRepository, never()).findQueueRows(any(), any());
    }

    @Test
    void getMyReservations_userNotInDb_throws() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getMyReservations());

        assertEquals(ErrorCode.USERS_NOT_EXISTED, ex.getErrorCode());
    }

    static Stream<Arguments> keywordCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("   ", null),
                Arguments.of("  John  ", "%john%"),
                Arguments.of("Nguyen_A%", "%nguyen\\_a\\%%"),
                Arguments.of("a\\b", "%a\\\\b%")
        );
    }

    @ParameterizedTest
    @MethodSource("keywordCases")
    void getAllReservations_convertsKeywordToEscapedLikePattern(String keyword, String expectedPattern) {
        Pageable pageable = PageRequest.of(0, 20);
        when(reservationRepository.search(any(), any(), any())).thenReturn(Page.<Reservation>empty());

        service.getAllReservations(null, keyword, pageable);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(reservationRepository).search(isNull(), captor.capture(), eq(pageable));
        assertEquals(expectedPattern, captor.getValue());
    }

    @Test
    void getAllReservations_mapsPageAndFillsQueuePosition() {
        Pageable pageable = PageRequest.of(0, 20);
        Reservation pending = reservation(10L, member, ReservationStatus.PENDING);
        when(reservationRepository.search(ReservationStatus.PENDING, "%john%", pageable))
                .thenReturn(new PageImpl<>(List.of(pending), pageable, 1));
        when(reservationRepository.findQueueRows(Set.of(5L), ReservationStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 10L}));
        stubMapper();

        Page<ReservationResponse> result = service.getAllReservations(ReservationStatus.PENDING, "John", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().get(0).getQueuePosition()); // dau hang
    }

    @Test
    void getReservationsByBook_returnsThatBooksReservationsAsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Reservation accepted = reservation(11L, member, ReservationStatus.ACCEPTED);
        when(reservationRepository.findByBookId(5L, pageable))
                .thenReturn(new PageImpl<>(List.of(accepted), pageable, 1));
        stubMapper();

        Page<ReservationResponse> result = service.getReservationsByBook(5L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(11L, result.getContent().get(0).getId());
    }
}