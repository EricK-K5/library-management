package com.example.library_management.service;

import com.example.library_management.dto.request.FineCreationRequest;
import com.example.library_management.dto.request.WaiveFineRequest;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.FineMapper;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

// Quy tac tinh phat: 5.000 d/ngay, toi da 30 ngay
// Mat sach: gia sach (mac dinh 100.000) + phi xu ly 20.000
// FineMapper dung ban MapStruct that nen cac test co the kiem tra luon response tra ve cho client.
@ExtendWith(MockitoExtension.class)
class FineServiceTest {

    @Mock FineRepository fineRepository;
    @Mock BorrowRecordRepository borrowRecordRepository;
    @Mock UserRepository userRepository;
    @Mock ReservationService reservationService;

    FineMapper fineMapper = Mappers.getMapper(FineMapper.class);

    FineService service;

    final LocalDate today = LocalDate.now();

    User member;
    User staff;
    Book book;

    @BeforeEach
    void setUp() {
        // Thu tu tham so theo thu tu field trong FineService (@RequiredArgsConstructor)
        service = new FineService(fineRepository, borrowRecordRepository, userRepository, fineMapper, reservationService);

        member = User.builder().id("m1").username("john").fullName("John Doe").build();
        staff = User.builder().id("s1").username("librarian").build();
        book = Book.builder().id(5L).title("Clean Code").build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("librarian", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // helper
    private void loginStaff() {
        when(userRepository.findByUsername("librarian")).thenReturn(Optional.of(staff));
    }

    /** Record co han tra cach hom nay `daysLate` ngay (am = chua toi han). */
    private BorrowRecord record(BorrowStatus status, long daysLate) {
        return BorrowRecord.builder().id(1L).user(member).book(book)
                .borrowDate(today.minusDays(daysLate + 7)).dueDate(today.minusDays(daysLate)).status(status).build();
    }

    private Fine fine(Long id, FineStatus status, BorrowRecord record) {
        return Fine.builder().id(id).user(member).borrowRecord(record).amount(new BigDecimal("15000"))
                .reason(FineReason.OVERDUE).status(status).issuedDate(today.minusDays(1)).build();
    }

    private void stubSaveEcho() {
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Fine savedFine() {
        ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
        verify(fineRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "so tien phat: " + actual);
    }

    // createOrUpdateOverdueFine

    @ParameterizedTest
    @ValueSource(longs = {0, -3})
    void createOrUpdateOverdueFine_notLate_createsNothing(long daysLate) {
        // Den dung ngay han (0) hoac chua toi han (-3) van hop le -> khong phat
        service.createOrUpdateOverdueFine(record(BorrowStatus.BORROWED, daysLate), today);

        verifyNoInteractions(fineRepository);
    }

    @Test
    void createOrUpdateOverdueFine_threeDaysLate_createsProvisionalFine() {
        BorrowRecord record = record(BorrowStatus.BORROWED, 3);
        stubSaveEcho();

        service.createOrUpdateOverdueFine(record, today);

        Fine fine = savedFine();
        assertAmount("15000", fine.getAmount());
        assertEquals("Tam tinh: tre han 3 ngay", fine.getNote());
        assertEquals(FineReason.OVERDUE, fine.getReason());
        assertEquals(FineStatus.UNPAID, fine.getStatus());
        assertEquals(today, fine.getIssuedDate());
        assertSame(member, fine.getUser());
        assertSame(record, fine.getBorrowRecord());
        assertEquals(1, record.getFines().size());                      // gan vao record de 2 chieu nhat quan
    }

    @ParameterizedTest
    @EnumSource(value = BorrowStatus.class, names = {"RETURNED", "LOST"})
    void createOrUpdateOverdueFine_finalizedRecord_noteHasNoProvisionalPrefix(BorrowStatus status) {
        stubSaveEcho();

        service.createOrUpdateOverdueFine(record(status, 3), today);

        assertEquals("Tre han 3 ngay", savedFine().getNote());
    }

    @Test
    void createOrUpdateOverdueFine_exactlyAtCap_noCapNote() {
        stubSaveEcho();

        service.createOrUpdateOverdueFine(record(BorrowStatus.OVERDUE, 30), today);

        Fine fine = savedFine();
        assertAmount("150000", fine.getAmount());
        assertEquals("Tam tinh: tre han 30 ngay", fine.getNote());
    }

    @Test
    void createOrUpdateOverdueFine_beyondCap_isCappedAt30Days() {
        stubSaveEcho();

        service.createOrUpdateOverdueFine(record(BorrowStatus.OVERDUE, 45), today);

        Fine fine = savedFine();
        assertAmount("150000", fine.getAmount());
        assertEquals("Tam tinh: tre han 45 ngay (tinh toi da 30 ngay)", fine.getNote());
    }

    @Test
    void createOrUpdateOverdueFine_existingUnpaidFine_isUpdatedNotDuplicated() {
        BorrowRecord record = record(BorrowStatus.OVERDUE, 4);
        Fine existing = fine(9L, FineStatus.UNPAID, record);
        existing.setAmount(new BigDecimal("5000"));
        existing.setNote("cu");
        when(fineRepository.findByBorrowRecordIdAndReason(1L, FineReason.OVERDUE)).thenReturn(Optional.of(existing));

        service.createOrUpdateOverdueFine(record, today);

        assertSame(existing, savedFine());                              // cung ban ghi, khong tao moi
        assertAmount("20000", existing.getAmount());                    // cap nhat theo so ngay moi
        assertEquals("Tam tinh: tre han 4 ngay", existing.getNote());
        assertTrue(record.getFines().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = FineStatus.class, names = {"PAID", "WAIVED"})
    void createOrUpdateOverdueFine_existingSettledFine_isLeftUntouched(FineStatus status) {
        BorrowRecord record = record(BorrowStatus.OVERDUE, 10);
        Fine existing = fine(9L, status, record);
        when(fineRepository.findByBorrowRecordIdAndReason(1L, FineReason.OVERDUE)).thenReturn(Optional.of(existing));

        service.createOrUpdateOverdueFine(record, today);

        // Da thu tien / da mien thi khong bao gio doi so tien nua
        assertAmount("15000", existing.getAmount());
        verify(fineRepository, never()).save(any());
    }

    // createOrUpdateLostBookFine

    @Test
    void createOrUpdateLostBookFine_withOverride_usesLibrariansAmount() {
        stubSaveEcho();

        service.createOrUpdateLostBookFine(record(BorrowStatus.LOST, 0), book, new BigDecimal("250000"), today, false);

        Fine fine = savedFine();
        assertAmount("250000", fine.getAmount());
        assertEquals(FineReason.LOST_BOOK, fine.getReason());
        assertEquals("Mat sach: so tien do thu thu dinh gia", fine.getNote());
    }

    @Test
    void createOrUpdateLostBookFine_withBookPrice_isPricePlusProcessingFee() {
        book.setPrice(new BigDecimal("80000"));
        stubSaveEcho();

        service.createOrUpdateLostBookFine(record(BorrowStatus.LOST, 0), book, null, today, false);

        Fine fine = savedFine();
        assertAmount("100000", fine.getAmount());                       // 80.000 + 20.000
        assertEquals("Mat sach: gia sach 80000 + phi xu ly 20000", fine.getNote());
    }

    @Test
    void createOrUpdateLostBookFine_bookWithoutPrice_usesDefault100000() {
        book.setPrice(null);
        stubSaveEcho();

        service.createOrUpdateLostBookFine(record(BorrowStatus.LOST, 0), book, null, today, false);

        Fine fine = savedFine();
        assertAmount("120000", fine.getAmount());                       // 100.000 mac dinh + 20.000
        assertTrue(fine.getNote().contains("(mac dinh, chua nhap gia)"));
    }

    @Test
    void createOrUpdateLostBookFine_auto_prefixesNote() {
        stubSaveEcho();

        service.createOrUpdateLostBookFine(record(BorrowStatus.LOST, 0), book, new BigDecimal("1"), today, true);

        assertTrue(savedFine().getNote().startsWith("Tu dong. "));
    }

    // refreshOverdueFineEstimates (scheduler hang ngay)

    @Test
    void refreshOverdueFineEstimates_updatesEveryHoldingRecordPastDueAndReturnsCount() {
        BorrowRecord r1 = record(BorrowStatus.BORROWED, 2);
        BorrowRecord r2 = record(BorrowStatus.OVERDUE, 10);
        r2.setId(2L);
        when(borrowRecordRepository.findByStatusInAndDueDateBefore(
                List.of(BorrowStatus.BORROWED, BorrowStatus.OVERDUE), today)).thenReturn(List.of(r1, r2));
        stubSaveEcho();

        int count = service.refreshOverdueFineEstimates();

        assertEquals(2, count);
        ArgumentCaptor<Fine> captor = ArgumentCaptor.forClass(Fine.class);
        verify(fineRepository, times(2)).save(captor.capture());
        assertAmount("10000", captor.getAllValues().get(0).getAmount()); // 2 ngay
        assertAmount("50000", captor.getAllValues().get(1).getAmount()); // 10 ngay
    }

    // Truy van

    @Test
    void hasUnpaidFine_delegatesToRepository() {
        when(fineRepository.existsByUserIdAndStatus("m1", FineStatus.UNPAID)).thenReturn(true);

        assertTrue(service.hasUnpaidFine("m1"));
        assertFalse(service.hasUnpaidFine("m2"));
    }

    @Test
    void getMyFines_returnsOnlyCurrentUsersFinesMapped() {
        loginStaff(); // nguoi dang nhap la user s1
        Fine mine = Fine.builder().id(3L).user(staff).borrowRecord(record(BorrowStatus.RETURNED, 1))
                .amount(new BigDecimal("5000")).reason(FineReason.OVERDUE).status(FineStatus.UNPAID).build();
        when(fineRepository.findByUserId("s1")).thenReturn(List.of(mine));

        List<FineResponse> result = service.getMyFines();

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getId());
        assertEquals("librarian", result.get(0).getUsername());
    }

    static Stream<Arguments> keywordCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("   ", null),
                Arguments.of("  Nguyen  ", "Nguyen")   // chi trim; LOWER/CONCAT do JPQL tu xu ly
        );
    }

    @ParameterizedTest
    @MethodSource("keywordCases")
    void getFines_blankKeywordMeansNoFilter_otherwiseTrimmed(String keyword, String expected) {
        Pageable pageable = PageRequest.of(0, 20);
        when(fineRepository.searchFines(any(), any(), any())).thenReturn(Page.<Fine>empty());

        service.getFines(keyword, FineStatus.UNPAID, pageable);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(fineRepository).searchFines(captor.capture(), eq(FineStatus.UNPAID), eq(pageable));
        assertEquals(expected, captor.getValue());
    }

    @Test
    void getFines_mapsPageToResponses() {
        Pageable pageable = PageRequest.of(0, 20);
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.searchFines(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(fine), pageable, 1));

        Page<FineResponse> result = service.getFines(null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("john", result.getContent().get(0).getUsername());
    }

    @Test
    void getFineById_found_mapsAllFieldsIncludingPayableOnline() {
        // Sach da tra + phat chua dong -> cho phep thanh toan online
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));

        FineResponse response = service.getFineById(3L);

        assertEquals(3L, response.getId());
        assertEquals(1L, response.getBorrowRecordId());
        assertEquals("m1", response.getUserId());
        assertEquals("john", response.getUsername());
        assertEquals("Clean Code", response.getBookTitle());
        assertAmount("15000", response.getAmount());
        assertTrue(response.isPayableOnline());
        assertNull(response.getProcessedByUsername());
    }

    @Test
    void getFineById_stillBorrowedBook_isNotPayableOnline() {
        // Phat tam tinh cua sach dang muon con tang theo ngay -> chua cho tra online
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.OVERDUE, 2));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));

        assertFalse(service.getFineById(3L).isPayableOnline());
    }

    @Test
    void getFineById_notFound_throwsNotExisted() {
        when(fineRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getFineById(99L));

        assertEquals(ErrorCode.FINE_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void getTotalUnpaidAmount_returnsRepositorySum() {
        when(fineRepository.sumAmountByStatus(FineStatus.UNPAID)).thenReturn(new BigDecimal("125000"));

        assertAmount("125000", service.getTotalUnpaidAmount());
    }

    // payFine (thu tien tai quay)

    @Test
    void payFine_unpaid_marksPaidRecordsStaffAndPromotesWaitingQueues() {
        loginStaff();
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));
        stubSaveEcho();

        FineResponse response = service.payFine(3L);

        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(today, fine.getPaidDate());
        assertSame(staff, fine.getProcessedBy());
        assertEquals(FineStatus.PAID, response.getStatus());
        assertEquals("librarian", response.getProcessedByUsername());

        // User vua het no -> phai xet lai hang doi dat sach, va chi sau khi da luu trang thai moi
        InOrder inOrder = inOrder(fineRepository, reservationService);
        inOrder.verify(fineRepository).save(fine);
        inOrder.verify(reservationService).promoteQueuesForUser("m1");
    }

    @ParameterizedTest
    @EnumSource(value = FineStatus.class, names = {"PAID", "WAIVED"})
    void payFine_notUnpaid_throwsAndChangesNothing(FineStatus status) {
        loginStaff();
        Fine fine = fine(3L, status, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));

        AppException ex = assertThrows(AppException.class, () -> service.payFine(3L));

        assertEquals(ErrorCode.FINE_NOT_UNPAID, ex.getErrorCode());
        assertEquals(status, fine.getStatus());
        verify(fineRepository, never()).save(any());
        verifyNoInteractions(reservationService);
    }

    @Test
    void payFine_notFound_throwsNotExisted() {
        loginStaff();
        when(fineRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.payFine(99L));

        assertEquals(ErrorCode.FINE_NOT_EXISTED, ex.getErrorCode());
    }

    // waiveFine (mien phat)

    @Test
    void waiveFine_unpaid_marksWaivedAppendsReasonToNoteAndPromotesQueues() {
        loginStaff();
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        fine.setNote("Tre han 3 ngay");
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));
        stubSaveEcho();

        FineResponse response = service.waiveFine(3L, WaiveFineRequest.builder().reason("Hoan canh kho khan").build());

        assertEquals(FineStatus.WAIVED, fine.getStatus());
        assertSame(staff, fine.getProcessedBy());
        assertNull(fine.getPaidDate());                                 // mien phat khong phai la thu tien
        assertEquals("Tre han 3 ngay | Mien: Hoan canh kho khan", fine.getNote());
        assertEquals(FineStatus.WAIVED, response.getStatus());
        verify(reservationService).promoteQueuesForUser("m1");
    }

    @Test
    void waiveFine_noExistingNote_noteIsJustTheReason() {
        loginStaff();
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));
        stubSaveEcho();

        service.waiveFine(3L, WaiveFineRequest.builder().reason("Loi he thong").build());

        assertEquals("Mien: Loi he thong", fine.getNote());
    }

    @Test
    void waiveFine_mergedNoteLongerThan255_isTruncated() {
        loginStaff();
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        fine.setNote("a".repeat(250));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));
        stubSaveEcho();

        service.waiveFine(3L, WaiveFineRequest.builder().reason("ly do dai").build());

        assertEquals(255, fine.getNote().length()); // cot note chi dai 255
        assertTrue(fine.getNote().startsWith("a".repeat(250)));
    }

    @ParameterizedTest
    @EnumSource(value = FineStatus.class, names = {"PAID", "WAIVED"})
    void waiveFine_notUnpaid_throws(FineStatus status) {
        loginStaff();
        Fine fine = fine(3L, status, record(BorrowStatus.RETURNED, 1));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));

        AppException ex = assertThrows(AppException.class,
                () -> service.waiveFine(3L, WaiveFineRequest.builder().reason("x").build()));

        assertEquals(ErrorCode.FINE_NOT_UNPAID, ex.getErrorCode());
        verifyNoInteractions(reservationService);
    }

    // markPaidOnline (VNPay)

    @Test
    void markPaidOnline_withTransactionNo_marksPaidAppendsNoteAndPromotesQueues() {
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        fine.setNote("Tre han 3 ngay");

        service.markPaidOnline(fine, "14012345");

        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(today, fine.getPaidDate());
        assertNull(fine.getProcessedBy());                              // khong co nhan vien nao xu ly
        assertEquals("Tre han 3 ngay | Thanh toan VNPay #14012345", fine.getNote());
        verify(fineRepository).save(fine);
        verify(reservationService).promoteQueuesForUser("m1");
    }

    @Test
    void markPaidOnline_blankTransactionNoAndNoNote_usesPlainNote() {
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));

        service.markPaidOnline(fine, "  ");

        assertEquals("Thanh toan VNPay", fine.getNote());
    }

    @Test
    void markPaidOnline_longNote_isTruncatedTo255() {
        Fine fine = fine(3L, FineStatus.UNPAID, record(BorrowStatus.RETURNED, 1));
        fine.setNote("a".repeat(250));

        service.markPaidOnline(fine, "999");

        assertEquals(255, fine.getNote().length());
    }

    // createManualFine (phat thu cong)

    private FineCreationRequest manualRequest(FineReason reason) {
        return FineCreationRequest.builder().borrowRecordId(1L).amount(new BigDecimal("30000"))
                .reason(reason).note("Rach bia").build();
    }

    @Test
    void createManualFine_damagedBook_createsUnpaidFineForTheBorrower() {
        loginStaff();
        BorrowRecord record = record(BorrowStatus.RETURNED, 0);
        when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(fineRepository.save(any(Fine.class))).thenAnswer(inv -> {
            Fine f = inv.getArgument(0);
            f.setId(7L);
            return f;
        });

        FineResponse response = service.createManualFine(manualRequest(FineReason.DAMAGED_BOOK));

        Fine fine = savedFine();
        assertSame(member, fine.getUser());                              // phat nguoi da muon, khong phai thu thu
        assertSame(record, fine.getBorrowRecord());
        assertAmount("30000", fine.getAmount());
        assertEquals(FineReason.DAMAGED_BOOK, fine.getReason());
        assertEquals(FineStatus.UNPAID, fine.getStatus());
        assertEquals(today, fine.getIssuedDate());
        assertSame(staff, fine.getProcessedBy());
        assertEquals("Rach bia", fine.getNote());
        assertEquals(7L, response.getId());
    }

    @ParameterizedTest
    @EnumSource(value = FineReason.class, names = {"OVERDUE", "LOST_BOOK"})
    void createManualFine_systemManagedReason_isRejectedBeforeAnyLookup(FineReason reason) {
        loginStaff();

        AppException ex = assertThrows(AppException.class, () -> service.createManualFine(manualRequest(reason)));

        // Phat qua han / mat sach chi he thong duoc tu tinh, thu thu khong tao tay
        assertEquals(ErrorCode.FINE_REASON_NOT_ALLOWED, ex.getErrorCode());
        verifyNoInteractions(borrowRecordRepository);
        verify(fineRepository, never()).save(any());
    }

    @Test
    void createManualFine_borrowRecordNotFound_throws() {
        loginStaff();
        when(borrowRecordRepository.findById(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.createManualFine(manualRequest(FineReason.DAMAGED_BOOK)));

        assertEquals(ErrorCode.BORROW_RECORD_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void createManualFine_alreadyHasDamagedFine_throwsAlreadyExisted() {
        loginStaff();
        when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record(BorrowStatus.RETURNED, 0)));
        when(fineRepository.existsByBorrowRecordIdAndReason(1L, FineReason.DAMAGED_BOOK)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.createManualFine(manualRequest(FineReason.DAMAGED_BOOK)));

        assertEquals(ErrorCode.FINE_ALREADY_EXISTED, ex.getErrorCode()); // moi record toi da 1 fine cho moi ly do
        verify(fineRepository, never()).save(any());
    }
}