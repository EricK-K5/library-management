package com.example.library_management.entity;

import com.example.library_management.enums.BookStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Size(max = 255)
    @Column(nullable = false)
    String title;

    @Column(unique = true, length = 20)
    String isbn;

    @Column(nullable = false)
    String author;

    @Column(length = 150)
    String publisher;

    @Column(name = "publish_year")
    Integer publishYear;

    @Column(length = 20)
    String language;

    @Column(length = 1000)
    String description;

    @Column(name = "cover_image_url")
    String coverImageUrl;

    @Column(name = "total_copies", nullable = false)
    @Builder.Default
    Integer totalCopies = 1;

    @Column(name = "available_copies", nullable = false)
    @Builder.Default
    Integer availableCopies = 1;

    @Column(name = "shelf_location", length = 50)
    String shelfLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    BookStatus status = BookStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    Category category;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    Set<BorrowRecord> borrowRecords = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    Set<Reservation> reservations = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @Column(precision = 12, scale = 2)
    BigDecimal price;
}
