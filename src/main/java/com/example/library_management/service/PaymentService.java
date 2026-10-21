package com.example.library_management.service;

import com.example.library_management.configuration.VnpConfig;
import com.example.library_management.dto.request.PaymentRequest;
import com.example.library_management.dto.response.PaymentResponse;
import com.example.library_management.entity.Fine;
import com.example.library_management.entity.PaymentTransaction;
import com.example.library_management.entity.User;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.enums.PaymentStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.PaymentTransactionRepository;
import com.example.library_management.repository.UserRepository;
import com.example.library_management.util.VnpayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class PaymentService {
    VnpConfig vnpConfig;
    FineRepository fineRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    UserRepository userRepository;
    FineService fineService;

    // VNPay bat buoc gio Viet Nam (GMT+7), khong dung gio may chu
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int EXPIRE_MINUTES = 15;
    

    public enum Code {
        PROCESSED,          // xu ly xong (thanh cong hoac that bai, xem txStatus)
        ALREADY_CONFIRMED,  // giao dich da duoc xu ly truoc do (IPN den truoc Return hoac nguoc lai)
        NOT_FOUND,          // khong co vnp_TxnRef nay
        INVALID_AMOUNT,     // vnp_Amount khong khop so tien luc tao giao dich
        INVALID_SIGNATURE   // sai chu ky
    }

    public record CallbackResult(Code code, String txnRef, Long fineId, PaymentStatus txStatus) {
        public boolean paid() {
            return txStatus == PaymentStatus.SUCCESS;
        }
    }

    // MEMBER tu thanh toan khoan phat cua chinh minh.
    // So tien lay tu DB, khong tin so tien client gui len.
    @Transactional
    public PaymentResponse createVnpayPayment(Long fineId, HttpServletRequest httpRequest) {
        if (!vnpConfig.enabled()) {
            throw new AppException(ErrorCode.PAYMENT_DISABLED);
        }

        User user = getCurrentUser();
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new AppException(ErrorCode.FINE_NOT_EXISTED));

        // Khoan phat cua nguoi khac -> coi nhu khong ton tai
        if (!fine.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.FINE_NOT_EXISTED);
        }
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw new AppException(ErrorCode.FINE_NOT_UNPAID);
        }
        if (!fine.isPayableOnline()) {
            throw new AppException(ErrorCode.FINE_NOT_PAYABLE_ONLINE);
        }

        BigDecimal amount = fine.getAmount().setScale(0, RoundingMode.HALF_UP);
        String txnRef = UUID.randomUUID().toString().replace("-", "");

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .txnRef(txnRef)
                .fine(fine)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build());

        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", vnpConfig.version());
        params.put("vnp_Command", vnpConfig.command());
        params.put("vnp_TmnCode", vnpConfig.tmnCode());
        // VNPay yeu cau nhan 100, bo phan thap phan
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        // Khong dau: ky tu non-ASCII bi encode thanh '?' khi ky
        params.put("vnp_OrderInfo", "Thanh toan tien phat #" + fine.getId());
        params.put("vnp_OrderType", vnpConfig.orderType());
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnpConfig.returnUrl());
        params.put("vnp_IpAddr", VnpayUtil.getIpAddress(httpRequest));
        params.put("vnp_CreateDate", now.format(VNP_DATE));
        params.put("vnp_ExpireDate", now.plusMinutes(EXPIRE_MINUTES).format(VNP_DATE));

        String secureHash = VnpayUtil.hmacSHA512(
                vnpConfig.hashSecret(), VnpayUtil.buildHashData(params));
        String paymentUrl = vnpConfig.payUrl() + "?" + VnpayUtil.buildQueryString(params)
                + "&vnp_SecureHash=" + secureHash;

        return new PaymentResponse(paymentUrl, txnRef);
    }

    // Dung chung cho Return URL va IPN. Idempotent: goi nhieu lan van chi cap nhat 1 lan.
    @Transactional
    public CallbackResult processCallback(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");

        if (!VnpayUtil.verifySignature(vnpConfig.hashSecret(), params)) {
            log.warn("VNPay callback with invalid signature, txnRef={}", txnRef);
            return new CallbackResult(Code.INVALID_SIGNATURE, txnRef, null, null);
        }

        PaymentTransaction tx = txnRef == null ? null
                : paymentTransactionRepository.findByTxnRefForUpdate(txnRef).orElse(null);
        if (tx == null) {
            return new CallbackResult(Code.NOT_FOUND, txnRef, null, null);
        }

        Long fineId = tx.getFine().getId();

        if (!isSameAmount(tx, params.get("vnp_Amount"))) {
            log.warn("VNPay callback amount mismatch, txnRef={}", txnRef);
            return new CallbackResult(Code.INVALID_AMOUNT, txnRef, fineId, tx.getStatus());
        }

        if (tx.getStatus() != PaymentStatus.PENDING) {
            return new CallbackResult(Code.ALREADY_CONFIRMED, txnRef, fineId, tx.getStatus());
        }

        tx.setResponseCode(params.get("vnp_ResponseCode"));
        tx.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        tx.setBankCode(params.get("vnp_BankCode"));

        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

        if (!success) {
            tx.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(tx);
            return new CallbackResult(Code.PROCESSED, txnRef, fineId, PaymentStatus.FAILED);
        }

        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAt(LocalDateTime.now());

        // Khoa dong Fine: tranh dam voi thu thu thu tien tai quay cung luc
        Fine fine = fineRepository.findByIdForUpdate(fineId)
                .orElseThrow(() -> new AppException(ErrorCode.FINE_NOT_EXISTED));

        if (fine.getStatus() == FineStatus.UNPAID) {
            fineService.markPaidOnline(fine, tx.getVnpTransactionNo());
        } else {
            // Khach da bi tru tien nhung khoan phat da PAID/WAIVED truoc do -> can hoan tien thu cong
            tx.setNote("Fine da o trang thai " + fine.getStatus() + " khi nhan tien, can hoan tien");
            log.warn("VNPay paid but fine {} is already {}, txnRef={}", fineId, fine.getStatus(), txnRef);
        }

        paymentTransactionRepository.save(tx);
        return new CallbackResult(Code.PROCESSED, txnRef, fineId, PaymentStatus.SUCCESS);
    }

    private boolean isSameAmount(PaymentTransaction tx, String vnpAmount) {
        if (vnpAmount == null) {
            return false;
        }
        try {
            BigDecimal expected = tx.getAmount().multiply(BigDecimal.valueOf(100));
            return expected.compareTo(new BigDecimal(vnpAmount)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERS_NOT_EXISTED));
    }
}
