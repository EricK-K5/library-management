package com.example.library_management.entity;

import com.example.library_management.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Mot lan thanh toan online (VNPay) cho 1 khoan phat.
// txnRef (vnp_TxnRef) la khoa de map callback cua VNPay ve dung Fine.
@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

//    ma dinh danh
    @Column(name = "txn_ref", nullable = false, unique = true, length = 64)
    String txnRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_id", nullable = false)
    Fine fine;

    // So tien VND (khong nhan 100)
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    // vnp_TransactionNo: ma giao dich ben VNPay
    @Column(name = "vnp_transaction_no", length = 30)
    String vnpTransactionNo;

    @Column(name = "bank_code", length = 30)
    String bankCode;

//    ma phan hoi
    @Column(name = "response_code", length = 10)
    String responseCode;

    @Column(length = 255)
    String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

//    tg thanh toan thanh cong
    @Column(name = "paid_at")
    LocalDateTime paidAt;
}
