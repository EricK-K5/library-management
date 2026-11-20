package com.example.library_management.service;

import com.example.library_management.configuration.VnpConfig;
import com.example.library_management.dto.response.PaymentResponse;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.PaymentTransaction;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.enums.Code;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.PaymentStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.PaymentTransactionRepository;
import com.example.library_management.repository.UserRepository;
import com.example.library_management.service.PaymentService.CallbackResult;
import com.example.library_management.util.VnpayUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// chu ky dung VnpayUtil THAT
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    static final String SECRET = "test-secret";

    @Mock FineRepository fineRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock UserRepository userRepository;
    @Mock FineService fineService;

    PaymentService service;

    User member;
    BorrowRecord record;
    Fine fine;

    private static VnpConfig config(boolean enabled) {
        return new VnpConfig(enabled, "https://sandbox.example/pay", "TMN123", SECRET,
                "http://localhost:8080/api/vnpay/return", "http://localhost:5173/result", "2.1.0", "pay", "other");
    }

    @BeforeEach
    void setUp() {
        // Thu tu tham so theo thu tu field trong PaymentService (@RequiredArgsConstructor)
        service = new PaymentService(config(true), fineRepository, paymentTransactionRepository, userRepository, fineService);

        member = User.builder().id("m1").username("john").build();
        record = BorrowRecord.builder().id(1L).user(member).status(BorrowStatus.RETURNED).build();
        fine = Fine.builder().id(3L).user(member).borrowRecord(record).amount(new BigDecimal("30000"))
                .reason(FineReason.OVERDUE).status(FineStatus.UNPAID).build();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("john", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubCurrentUserAndFine() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(member));
        when(fineRepository.findById(3L)).thenReturn(Optional.of(fine));
    }

    private static MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");
        return request;
    }

    private static Map<String, String> parseQuery(String url) {
        Map<String, String> map = new HashMap<>();
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            int i = pair.indexOf('=');
            map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    // createVnpayPayment

    @Test
    void createVnpayPayment_success_savesPendingTransactionAndBuildsSignedUrl() {
//        lam tron -> 30.001
        fine.setAmount(new BigDecimal("30000.50"));
        stubCurrentUserAndFine();

        PaymentResponse response = service.createVnpayPayment(3L, httpRequest());

        //1 Giao dich PENDING duoc luu voi so tien LAY TU DB
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction tx = captor.getValue();
        assertEquals(PaymentStatus.PENDING, tx.getStatus());
        assertEquals(0, new BigDecimal("30001").compareTo(tx.getAmount()));
        assertEquals(fine, tx.getFine());
        assertEquals(response.txnRef(), tx.getTxnRef());
        assertEquals(32, tx.getTxnRef().length()); // UUID bo dau gach

        //2 URL thanh toan
        assertTrue(response.paymentUrl().startsWith("https://sandbox.example/pay?"));
        Map<String, String> params = parseQuery(response.paymentUrl());
        assertEquals("3000100", params.get("vnp_Amount"));
        assertEquals("VND", params.get("vnp_CurrCode"));
        assertEquals("TMN123", params.get("vnp_TmnCode"));
        assertEquals(tx.getTxnRef(), params.get("vnp_TxnRef"));
        assertEquals("Thanh toan tien phat #3", params.get("vnp_OrderInfo"));
        assertEquals("203.0.113.5", params.get("vnp_IpAddr"));
        assertTrue(params.get("vnp_ExpireDate").compareTo(params.get("vnp_CreateDate")) > 0); // het han sau luc tao
        assertTrue(VnpayUtil.verifySignature(SECRET, new TreeMap<>(params)));
    }

    @Test
    void createVnpayPayment_disabled_throwsBeforeAnyLookup() {
        service = new PaymentService(config(false), fineRepository, paymentTransactionRepository, userRepository, fineService);

        AppException ex = assertThrows(AppException.class, () -> service.createVnpayPayment(3L, httpRequest()));

        assertEquals(ErrorCode.PAYMENT_DISABLED, ex.getErrorCode());
        verifyNoInteractions(userRepository, fineRepository, paymentTransactionRepository);
    }

    @Test
    void createVnpayPayment_someoneElsesFine_looksLikeItDoesNotExist() {
        fine.setUser(User.builder().id("m2").username("other").build());
        stubCurrentUserAndFine();

        AppException ex = assertThrows(AppException.class, () -> service.createVnpayPayment(3L, httpRequest()));

        // Khong tiet lo khoan phat do co ton tai hay khong
        assertEquals(ErrorCode.FINE_NOT_EXISTED, ex.getErrorCode());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createVnpayPayment_bookNotReturnedYet_isNotPayableOnline() {
        record.setStatus(BorrowStatus.OVERDUE); // phat tam tinh con tang theo ngay -> chua cho tra online
        stubCurrentUserAndFine();

        AppException ex = assertThrows(AppException.class, () -> service.createVnpayPayment(3L, httpRequest()));

        assertEquals(ErrorCode.FINE_NOT_PAYABLE_ONLINE, ex.getErrorCode());
        verify(paymentTransactionRepository, never()).save(any());
    }

    // processCallback (Return URL + IPN dung chung)

    private PaymentTransaction tx(PaymentStatus status) {
        return PaymentTransaction.builder().id(1L).txnRef("REF1").fine(fine)
                .amount(new BigDecimal("30000")).status(status).build();
    }

    private void stubTx(PaymentTransaction tx) {
        when(paymentTransactionRepository.findByTxnRefForUpdate("REF1")).thenReturn(Optional.of(tx));
    }

    /** Bo tham so VNPay gui ve, da ky bang dung secret. */
    private Map<String, String> callback(String amountX100, String responseCode, String transactionStatus) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TxnRef", "REF1");
        params.put("vnp_Amount", amountX100);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TransactionNo", "14012345");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", VnpayUtil.hmacSHA512(SECRET, VnpayUtil.buildHashData(params)));
        return params;
    }

    @Test
    void processCallback_invalidSignature_isRejectedWithoutTouchingDatabase() {
        Map<String, String> params = callback("3000000", "00", "00");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_Amount", "100"); // doi sau khi ky

        CallbackResult result = service.processCallback(params);

        assertEquals(Code.INVALID_SIGNATURE, result.code());
        assertFalse(result.paid());
        verifyNoInteractions(paymentTransactionRepository, fineRepository, fineService);
    }

    @Test
    void processCallback_unknownTxnRef_returnsNotFound() {
        // findByTxnRefForUpdate khong duoc stub -> Optional.empty
        CallbackResult result = service.processCallback(callback("3000000", "00", "00"));

        assertEquals(Code.NOT_FOUND, result.code());
        assertNull(result.fineId());
        verifyNoInteractions(fineService);
    }

    @Test
    void processCallback_validSignatureButWrongAmount_isRejectedAndNothingChanges() {
        PaymentTransaction tx = tx(PaymentStatus.PENDING);
        stubTx(tx);

        // Chu ky dung nhung so tien (1 dong) khac luc tao giao dich (30.000 dong)
        CallbackResult result = service.processCallback(callback("100", "00", "00"));

        assertEquals(Code.INVALID_AMOUNT, result.code());
        assertEquals(3L, result.fineId());
        assertEquals(PaymentStatus.PENDING, tx.getStatus());
        verify(paymentTransactionRepository, never()).save(any());
        verifyNoInteractions(fineService);
    }

    @Test
    void processCallback_success_marksTransactionSuccessAndFinePaid() {
        PaymentTransaction tx = tx(PaymentStatus.PENDING);
        stubTx(tx);
        when(fineRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(fine));

        CallbackResult result = service.processCallback(callback("3000000", "00", "00"));

        assertEquals(Code.PROCESSED, result.code());
        assertEquals(PaymentStatus.SUCCESS, result.txStatus());
        assertTrue(result.paid());
        assertEquals(3L, result.fineId());

        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        assertNotNull(tx.getPaidAt());
        assertEquals("00", tx.getResponseCode());
        assertEquals("14012345", tx.getVnpTransactionNo());
        assertEquals("NCB", tx.getBankCode());

        verify(fineService).markPaidOnline(fine, "14012345"); // Fine chuyen PAID qua FineService (xet lai hang doi dat sach)
        verify(paymentTransactionRepository).save(tx);
    }

    @ParameterizedTest
    @CsvSource({"24,02", "00,02", "24,00"})
    void processCallback_anyNon00Code_marksFailedAndNeverPaysTheFine(String responseCode, String transactionStatus) {
        // Chi thanh cong khi CA responseCode va transactionStatus deu la "00"
        PaymentTransaction tx = tx(PaymentStatus.PENDING);
        stubTx(tx);

        CallbackResult result = service.processCallback(callback("3000000", responseCode, transactionStatus));

        assertEquals(Code.PROCESSED, result.code());
        assertEquals(PaymentStatus.FAILED, result.txStatus());
        assertFalse(result.paid());
        assertEquals(PaymentStatus.FAILED, tx.getStatus());
        verify(paymentTransactionRepository).save(tx);
        verifyNoInteractions(fineService);
        verify(fineRepository, never()).findByIdForUpdate(any());
    }

//    xu ly viec callback giao dich lan 2 -> khong thanh toan lan 2
    @Test
    void processCallback_alreadyConfirmed_isIdempotentAndDoesNotPayTwice() {
        // Return URL va IPN co the cung ve: lan thu 2 khong duoc xu ly lai
        PaymentTransaction tx = tx(PaymentStatus.SUCCESS);
        stubTx(tx);

        CallbackResult result = service.processCallback(callback("3000000", "00", "00"));

        assertEquals(Code.ALREADY_CONFIRMED, result.code());
        assertEquals(PaymentStatus.SUCCESS, result.txStatus());
        verify(paymentTransactionRepository, never()).save(any());
        verifyNoInteractions(fineService);
    }

    @Test
    void processCallback_successButFineAlreadySettled_keepsFineAndFlagsRefund() {
        // Khach da bi tru tien nhung thu thu vua thu tien mat tai quay (fine da PAID)
        fine.setStatus(FineStatus.PAID);
        PaymentTransaction tx = tx(PaymentStatus.PENDING);
        stubTx(tx);
        when(fineRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(fine));

        CallbackResult result = service.processCallback(callback("3000000", "00", "00"));

        assertTrue(result.paid());
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());               // tien da vao that
        assertTrue(tx.getNote().contains("PAID") && tx.getNote().contains("hoan tien")); // danh dau de hoan tien thu cong
        verifyNoInteractions(fineService);                                  // khong danh dau PAID lan 2
        verify(paymentTransactionRepository).save(tx);
    }
}