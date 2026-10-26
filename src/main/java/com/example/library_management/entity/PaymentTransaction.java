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
    private Long id;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 64)
    private String txnRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_id", nullable = false)
    private Fine fine;

    // So tien VND (khong nhan 100)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // vnp_TransactionNo: ma giao dich ben VNPay
    @Column(name = "vnp_transaction_no", length = 30)
    private String vnpTransactionNo;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

//    tg thanh toan thanh cong
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
