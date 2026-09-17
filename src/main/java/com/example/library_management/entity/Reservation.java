package com.example.library_management.entity;

import com.example.library_management.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    Book book;

    @Column(name = "reservation_date", nullable = false)
    @Builder.Default
    LocalDate reservationDate = LocalDate.now();

    // Chi co gia tri khi status = ACCEPTED (han 2 ngay den lay)
    @Column(name = "expiry_date")
    LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    ReservationStatus status = ReservationStatus.PENDING;

    // Nguoi (LIBRARIAN/ADMIN) da xu ly accept/cancel thu cong, null neu he thong tu dong accept
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    User processedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}

