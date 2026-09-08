package com.example.library_management.entity;

import com.example.library_management.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
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

    @Column(name = "expiry_date", nullable = false)
    LocalDate expiryDate; // hết hạn giữ chỗ nếu không đến lấy

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    ReservationStatus status = ReservationStatus.PENDING;

    // Thứ tự trong hàng chờ nếu sách đang hết
    @Column(name = "queue_position")
    Integer queuePosition;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
