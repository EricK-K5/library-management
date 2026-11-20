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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowRecordServiceTest {

    @Mock BorrowRecordRepository borrowRecordRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationService reservationService;
    @Mock FineService fineService;

    // test nay chi can biet service co goi mapper va tra dung ket qua cua no hay khong
    @Mock BorrowRecordMapper borrowRecordMapper;
    @Mock MyBorrowRecordMapper myBorrowRecordMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    BorrowRecordService service;

    final LocalDate today = LocalDate.now();

    User staff;
    User member;
    Book book;
    final BorrowRecordResponse response = BorrowRecordResponse.builder().id(100L).build();

    @BeforeEach
    void setUp() {
        // Thu tu tham so theo thu tu field trong BorrowRecordService (@RequiredArgsConstructor)
        service = new BorrowRecordService(borrowRecordRepository, bookRepository, userRepository,
                reservationRepository, reservationService, fineService,
                borrowRecordMapper, myBorrowRecordMapper, eventPublisher);

        staff = User.builder().id("s1").username("librarian").build();
        member = User.builder().id("m1").username("john").fullName("John Doe")
                .email("john@example.com").maxBorrowLimit(5).build();
        book = Book.builder().id(5L).title("Clean Code").totalCopies(3).availableCopies(2).build();

        // Nguoi dang dang nhap = "librarian" (service lay tu SecurityContextHolder)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("librarian", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginStaff() {
        when(userRepository.findByUsername("librarian")).thenReturn(Optional.of(staff));
    }

    private BorrowRecord record(BorrowStatus status) {
        return BorrowRecord.builder().id(1L).user(member).book(book)
                .borrowDate(today.minusDays(3)).dueDate(today.plusDays(4)).status(status).build();
    }

    // handoverBook (giao sach cho reservation da ACCEPTED)

    private Reservation reservation(ReservationStatus status, LocalDate expiry) {
        Reservation r = Reservation.builder().id(1L).user(member).book(book)
                .status(status).expiryDate(expiry).build();
        when(reservationService.lockReservation(1L)).thenReturn(r);
        return r;
    }

    private void stubSlots(long borrowSlots, long reservationSlots) {
        when(borrowRecordRepository.countBorrowSlotsInUse("m1")).thenReturn(borrowSlots);
        when(reservationRepository.countByUserIdAndStatusIn(eq("m1"), any())).thenReturn(reservationSlots);
    }

    private HandoverRequest handoverRequest() {
        return HandoverRequest.builder().reservationId(1L).build();
    }

    private void assertHandoverRejected(ErrorCode expected) {
        AppException ex = assertThrows(AppException.class, () -> service.handoverBook(handoverRequest()));
        assertEquals(expected, ex.getErrorCode());
        verify(borrowRecordRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handoverBook_success_fulfillsReservationCreatesRecordAndSendsMail() {
        loginStaff();
        // Han giu cho het dung hom nay van con hop le (chi bi tu choi khi expiry < today)
        Reservation reservation = reservation(ReservationStatus.ACCEPTED, today);
        // 4 sach dang muon + 1 reservation chinh no = 5 = dung bang han muc -> van duoc phep (chi chan khi > limit)
        stubSlots(4, 1);
        when(borrowRecordRepository.save(any(BorrowRecord.class))).thenAnswer(inv -> {
            BorrowRecord r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });
        when(borrowRecordMapper.toBorrowRecordResponse(any(BorrowRecord.class))).thenReturn(response);

        BorrowRecordResponse result = service.handoverBook(handoverRequest());

        // Reservation chuyen sang FULFILLED va ghi nhan thu thu xu ly
        assertEquals(ReservationStatus.FULFILLED, reservation.getStatus());
        assertSame(staff, reservation.getProcessedBy());
        verify(reservationRepository).save(reservation);

        // BorrowRecord: han tra = ngay giao + 7 ngay, gan voi reservation
        ArgumentCaptor<BorrowRecord> captor = ArgumentCaptor.forClass(BorrowRecord.class);
        verify(borrowRecordRepository).save(captor.capture());
        BorrowRecord saved = captor.getValue();
        assertSame(member, saved.getUser());
        assertSame(book, saved.getBook());
        assertEquals(today, saved.getBorrowDate());
        assertEquals(today.plusDays(7), saved.getDueDate());
        assertSame(staff, saved.getIssuedBy());
        assertSame(staff, saved.getProcessedBy());
        assertSame(reservation, saved.getReservation());

        // Khong tru availableCopies lan 2 (da giu tu luc ACCEPTED)
        assertEquals(2, book.getAvailableCopies());

        // Mail thong bao duoc phat di voi dung noi dung
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MailEvents.BookBorrowed event = (MailEvents.BookBorrowed) eventCaptor.getValue();
        assertEquals("john@example.com", event.email());
        assertEquals("Clean Code", event.bookTitle());
        assertEquals(today.plusDays(7), event.dueDate());

        assertSame(response, result);
    }

    @Test
    void handoverBook_reservationPending_throwsNotAccepted() {
        loginStaff();
        reservation(ReservationStatus.PENDING, null);

        assertHandoverRejected(ErrorCode.RESERVATION_NOT_ACCEPTED);
    }

//    chay method 3 lan = 3 gia tri truyen vao
    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"FULFILLED", "CANCELLED", "EXPIRED"})
    void handoverBook_reservationAlreadyProcessed_throwsAlreadyProcessed(ReservationStatus status) {
        loginStaff();
        reservation(status, null);

        assertHandoverRejected(ErrorCode.RESERVATION_ALREADY_PROCESSED);
    }

    @Test
    void handoverBook_holdExpiredYesterday_throwsExpired() {
        loginStaff();
        reservation(ReservationStatus.ACCEPTED, today.minusDays(1));

        assertHandoverRejected(ErrorCode.RESERVATION_EXPIRED);
    }

    @Test
    void handoverBook_memberHasUnpaidFine_throwsUnpaidFine() {
        loginStaff();
        reservation(ReservationStatus.ACCEPTED, today.plusDays(1));
        when(fineService.hasUnpaidFine("m1")).thenReturn(true);

        assertHandoverRejected(ErrorCode.USER_HAS_UNPAID_FINE);
    }

    @Test
    void handoverBook_memberHasOverdueBook_throwsOverdueBook() {
        loginStaff();
        reservation(ReservationStatus.ACCEPTED, today.plusDays(1));
        when(borrowRecordRepository.existsByUserIdAndStatusInAndDueDateBefore(eq("m1"), any(), eq(today)))
                .thenReturn(true);

        assertHandoverRejected(ErrorCode.USER_HAS_OVERDUE_BOOK);
    }

    @Test
    void handoverBook_slotsAboveLimit_throwsLimitExceeded() {
        loginStaff();
        reservation(ReservationStatus.ACCEPTED, today.plusDays(1));
        stubSlots(5, 1); // 6 > 5

        assertHandoverRejected(ErrorCode.BORROW_LIMIT_EXCEEDED);
    }

    @Test
    void handoverBook_nullMaxBorrowLimit_usesDefaultLimitOf5() {
        loginStaff();
        member.setMaxBorrowLimit(null);
        reservation(ReservationStatus.ACCEPTED, today.plusDays(1));
        stubSlots(5, 1); // 6 > 5 (han muc mac dinh)

        assertHandoverRejected(ErrorCode.BORROW_LIMIT_EXCEEDED);
    }

    // returnBook (tra sach)

    private void stubFindRecordForUpdate(BorrowRecord record) {
        when(borrowRecordRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(record));
    }

    @ParameterizedTest
    @EnumSource(value = BorrowStatus.class, names = {"BORROWED", "OVERDUE"})
    void returnBook_borrowedOrOverdue_marksReturnedAndPromotesQueue(BorrowStatus status) {
        loginStaff();
        BorrowRecord record = record(status);
        stubFindRecordForUpdate(record);
        when(borrowRecordRepository.save(record)).thenReturn(record);
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);

        BorrowRecordResponse result = service.returnBook(1L, BorrowReturnRequest.builder().note("Sach con tot").build());

        assertEquals(BorrowStatus.RETURNED, record.getStatus());
        assertEquals(today, record.getReturnDate());
        assertSame(staff, record.getProcessedBy());
        assertSame(staff, record.getReceivedBy());
        assertEquals("Sach con tot", record.getNote());

        // Phai tinh phat TRUOC, roi moi tra sach ve kho / day hang doi (de quy tac kiem tra phat ap dung cho nguoi ke tiep)
        InOrder inOrder = inOrder(fineService, reservationService);
        inOrder.verify(fineService).createOrUpdateOverdueFine(record, today);
        inOrder.verify(reservationService).releaseAndPromote(5L);
        verify(borrowRecordRepository).save(record);
        assertSame(response, result);
    }

    @Test
    void returnBook_nullRequest_keepsExistingNote() {
        loginStaff();
        BorrowRecord record = record(BorrowStatus.BORROWED);
        record.setNote("ghi chu cu");
        stubFindRecordForUpdate(record);
        when(borrowRecordRepository.save(record)).thenReturn(record);

        service.returnBook(1L, null); // request la @RequestBody(required = false)

        assertEquals(BorrowStatus.RETURNED, record.getStatus());
        assertEquals("ghi chu cu", record.getNote());
    }

    @Test
    void returnBook_recordNotFound_throwsNotExisted() {
        loginStaff();
        when(borrowRecordRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.returnBook(99L, null));

        assertEquals(ErrorCode.BORROW_RECORD_NOT_EXISTED, ex.getErrorCode());
        verifyNoInteractions(fineService, reservationService);
    }

    @Test
    void returnBook_alreadyReturned_throwsAndDoesNotTouchFineOrQueue() {
        loginStaff();
        stubFindRecordForUpdate(record(BorrowStatus.RETURNED));

        AppException ex = assertThrows(AppException.class, () -> service.returnBook(1L, null));

        assertEquals(ErrorCode.BORROW_ALREADY_RETURNED, ex.getErrorCode());
        verifyNoInteractions(fineService, reservationService);
    }

    @Test
    void returnBook_alreadyLost_throwsAlreadyLost() {
        loginStaff();
        stubFindRecordForUpdate(record(BorrowStatus.LOST));

        AppException ex = assertThrows(AppException.class, () -> service.returnBook(1L, null));

        assertEquals(ErrorCode.BORROW_ALREADY_LOST, ex.getErrorCode());
        verifyNoInteractions(fineService, reservationService);
    }

    // markLost (bao mat sach)

    private void stubLockBook() {
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(book));
    }

    @Test
    void markLost_success_marksLostCreatesFinesAndDecreasesTotalCopies() {
        loginStaff();
        BorrowRecord record = record(BorrowStatus.BORROWED);
        stubFindRecordForUpdate(record);
        stubLockBook();
        when(borrowRecordRepository.save(record)).thenReturn(record);
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);
        BigDecimal amount = new BigDecimal("150000");

        BorrowRecordResponse result = service.markLost(1L,
                MarkLostRequest.builder().note("Lam mat").lostAmount(amount).build());

        assertEquals(BorrowStatus.LOST, record.getStatus());
        assertEquals(today, record.getLostDate());
        assertSame(staff, record.getProcessedBy());
        assertEquals("Lam mat", record.getNote());

        // Fine phat qua han + fine den sach (truyen dung so tien override, auto = false)
        verify(fineService).createOrUpdateOverdueFine(record, today);
        verify(fineService).createOrUpdateLostBookFine(record, book, amount, today, false);

        // Sach mat khong quay lai kho: chi giam tong so ban, khong tang so ban co san
        assertEquals(2, book.getTotalCopies());
        assertEquals(2, book.getAvailableCopies());
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
        verify(bookRepository).save(book);
        verify(borrowRecordRepository).save(record);
        assertSame(response, result);
    }

    @Test
    void markLost_lastCopy_bookBecomesOutOfStock() {
        loginStaff();
        book.setTotalCopies(1);
        book.setAvailableCopies(0); // ban duy nhat dang duoc muon
        BorrowRecord record = record(BorrowStatus.OVERDUE);
        stubFindRecordForUpdate(record);
        stubLockBook();

        service.markLost(1L, null);

        assertEquals(0, book.getTotalCopies());
        assertEquals(BookStatus.OUT_OF_STOCK, book.getStatus());
    }

    @Test
    void markLost_discontinuedBook_statusIsNotRestored() {
        loginStaff();
        book.setStatus(BookStatus.DISCONTINUED);
        stubFindRecordForUpdate(record(BorrowStatus.BORROWED));
        stubLockBook();

        service.markLost(1L, null);

        // Sach ngung phuc vu khong bi doi lai thanh AVAILABLE du con ban co san
        assertEquals(BookStatus.DISCONTINUED, book.getStatus());
    }

    @Test
    void markLost_nullRequest_passesNullAmountAndKeepsNote() {
        loginStaff();
        BorrowRecord record = record(BorrowStatus.BORROWED);
        record.setNote("ghi chu cu");
        stubFindRecordForUpdate(record);
        stubLockBook();

        service.markLost(1L, null);

        verify(fineService).createOrUpdateLostBookFine(eq(record), eq(book), isNull(), eq(today), eq(false));
        assertEquals("ghi chu cu", record.getNote());
    }

    @Test
    void markLost_recordNotFound_throwsNotExisted() {
        loginStaff();
        when(borrowRecordRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.markLost(1L, null));

        assertEquals(ErrorCode.BORROW_RECORD_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void markLost_alreadyLost_throwsBeforeLockingBook() {
        loginStaff();
        stubFindRecordForUpdate(record(BorrowStatus.LOST));

        AppException ex = assertThrows(AppException.class, () -> service.markLost(1L, null));

        assertEquals(ErrorCode.BORROW_ALREADY_LOST, ex.getErrorCode());
        verifyNoInteractions(bookRepository, fineService);
    }

    @Test
    void markLost_alreadyReturned_throwsCannotMarkLost() {
        loginStaff();
        stubFindRecordForUpdate(record(BorrowStatus.RETURNED));

        AppException ex = assertThrows(AppException.class, () -> service.markLost(1L, null));

        assertEquals(ErrorCode.BORROW_CANNOT_MARK_LOST, ex.getErrorCode());
        verifyNoInteractions(bookRepository, fineService);
    }

    @Test
    void markLost_bookNotFound_throwsAndLeavesRecordUntouched() {
        loginStaff();
        BorrowRecord record = record(BorrowStatus.BORROWED);
        stubFindRecordForUpdate(record);
        when(bookRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.markLost(1L, null));

        assertEquals(ErrorCode.BOOK_NOT_EXISTED, ex.getErrorCode());
        assertEquals(BorrowStatus.BORROWED, record.getStatus());
        verifyNoInteractions(fineService);
    }

    // Cac job cua scheduler

    @Test
    void markOverdueRecords_returnsNumberOfUpdatedRows() {
        when(borrowRecordRepository.markOverdue(BorrowStatus.OVERDUE, BorrowStatus.BORROWED, today)).thenReturn(7);

        assertEquals(7, service.markOverdueRecords());
    }

    @Test
    void findLongOverdueRecordIds_usesThresholdOf30DaysAndReturnsIds() {
        BorrowRecord r1 = record(BorrowStatus.OVERDUE);
        BorrowRecord r2 = record(BorrowStatus.OVERDUE);
        r2.setId(2L);
        when(borrowRecordRepository.findByStatusAndDueDateLessThanEqual(BorrowStatus.OVERDUE, today.minusDays(30)))
                .thenReturn(List.of(r1, r2));

        assertEquals(List.of(1L, 2L), service.findLongOverdueRecordIds());
    }

    @Test
    void autoMarkLost_overdueRecord_marksLostAsAutoWithoutStaff() {
        BorrowRecord record = record(BorrowStatus.OVERDUE);
        stubFindRecordForUpdate(record);
        stubLockBook();

        service.autoMarkLost(1L);

        assertEquals(BorrowStatus.LOST, record.getStatus());
        assertNull(record.getProcessedBy()); // he thong tu xu ly, khong co thu thu
        assertTrue(record.getNote().contains("30"));
        // auto = true, khong co so tien override
        verify(fineService).createOrUpdateLostBookFine(eq(record), eq(book), isNull(), eq(today), eq(true));
        assertEquals(2, book.getTotalCopies());
    }

    @ParameterizedTest
    @EnumSource(value = BorrowStatus.class, names = {"BORROWED", "RETURNED", "LOST"})
    void autoMarkLost_notOverdue_isSkipped(BorrowStatus status) {
        // Record da duoc tra / da bao mat trong luc job dang chay -> bo qua, khong loi
        BorrowRecord record = record(status);
        stubFindRecordForUpdate(record);

        service.autoMarkLost(1L);

        assertEquals(status, record.getStatus());
        verifyNoInteractions(bookRepository, fineService);
    }

    @Test
    void autoMarkLost_recordNotFound_isSkipped() {
        when(borrowRecordRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        service.autoMarkLost(99L);

        verifyNoInteractions(bookRepository, fineService);
    }

    // Truy van

    static Stream<Arguments> keywordCases() {
        return Stream.of(
                Arguments.of(null, null),                       // khong loc
                Arguments.of("   ", null),                      // chuoi trang = khong loc
                Arguments.of("  John  ", "%john%"),             // trim + lowercase + boc %...%
                Arguments.of("Nguyen_A%", "%nguyen\\_a\\%%"),   // escape _ va % de khong bi hieu la wildcard cua LIKE
                Arguments.of("a\\b", "%a\\\\b%")                // escape ca dau \
        );
    }

    @ParameterizedTest
    @MethodSource("keywordCases")
    void getAllBorrowRecords_convertsKeywordToEscapedLikePattern(String keyword, String expectedPattern) {
        Pageable pageable = PageRequest.of(0, 20);
        when(borrowRecordRepository.search(any(), any(), any())).thenReturn(Page.<BorrowRecord>empty());

        service.getAllBorrowRecords(null, keyword, pageable);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(borrowRecordRepository).search(isNull(), captor.capture(), eq(pageable));
        assertEquals(expectedPattern, captor.getValue());
    }

    @Test
    void getAllBorrowRecords_mapsEachRecordToResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        BorrowRecord record = record(BorrowStatus.BORROWED);
        when(borrowRecordRepository.search(BorrowStatus.BORROWED, "%john%", pageable))
                .thenReturn(new PageImpl<>(List.of(record), pageable, 1));
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);

        Page<BorrowRecordResponse> result = service.getAllBorrowRecords(BorrowStatus.BORROWED, "John", pageable);

        assertEquals(1, result.getTotalElements());
        assertSame(response, result.getContent().get(0));
    }

    @Test
    void getOverdueRecords_filtersBorrowedAndOverdueBeforeToday() {
        Pageable pageable = PageRequest.of(0, 20);
        BorrowRecord record = record(BorrowStatus.OVERDUE);
        when(borrowRecordRepository.findOverdue(
                List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE), today, "%clean%", pageable))
                .thenReturn(new PageImpl<>(List.of(record), pageable, 1));
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);

        Page<BorrowRecordResponse> result = service.getOverdueRecords("Clean", pageable);

        assertSame(response, result.getContent().get(0));
    }

    @Test
    void getBorrowRecordById_found_returnsMappedResponse() {
        BorrowRecord record = record(BorrowStatus.BORROWED);
        when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);

        assertSame(response, service.getBorrowRecordById(1L));
    }

    @Test
    void getBorrowRecordById_notFound_throwsNotExisted() {
        when(borrowRecordRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getBorrowRecordById(99L));

        assertEquals(ErrorCode.BORROW_RECORD_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void getBorrowRecordsByUser_userExists_returnsMappedList() {
        BorrowRecord record = record(BorrowStatus.BORROWED);
        when(userRepository.existsById("m1")).thenReturn(true);
        when(borrowRecordRepository.findByUserId("m1")).thenReturn(List.of(record));
        when(borrowRecordMapper.toBorrowRecordResponse(record)).thenReturn(response);

        List<BorrowRecordResponse> result = service.getBorrowRecordsByUser("m1");

        assertEquals(1, result.size());
        assertSame(response, result.get(0));
    }

    @Test
    void getBorrowRecordsByUser_userNotFound_throwsAndDoesNotQueryRecords() {
        when(userRepository.existsById("ghost")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.getBorrowRecordsByUser("ghost"));

        assertEquals(ErrorCode.USERS_NOT_EXISTED, ex.getErrorCode());
        verifyNoInteractions(borrowRecordRepository);
    }

    @Test
    void getMyBorrowRecords_returnsOnlyCurrentUsersRecords() {
        loginStaff(); // "nguoi dang nhap" la user s1
        BorrowRecord record = record(BorrowStatus.BORROWED);
        MyBorrowRecordResponse mine = MyBorrowRecordResponse.builder().id(1L).build();
        when(borrowRecordRepository.findByUserId("s1")).thenReturn(List.of(record));
        when(myBorrowRecordMapper.toMyBorrowRecordResponse(record)).thenReturn(mine);

        List<MyBorrowRecordResponse> result = service.getMyBorrowRecords();

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void getMyBorrowRecords_currentUserNotInDb_throwsUserNotExisted() {
        when(userRepository.findByUsername("librarian")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getMyBorrowRecords());

        assertEquals(ErrorCode.USERS_NOT_EXISTED, ex.getErrorCode());
        verifyNoInteractions(borrowRecordRepository);
    }
}