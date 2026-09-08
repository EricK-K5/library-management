package com.example.library_management.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 100)
    String code; // VD: BOOK_CREATE, BOOK_DELETE, USER_MANAGE, FINE_WAIVE

    @Column(length = 255)
    String description;

    // Nhóm quyền theo module để dễ quản lý trên UI
    @Column(length = 50)
    String module; // VD: BOOK, USER, BORROW, FINE, REPORT

    @ManyToMany(mappedBy = "permissions")
    @Builder.Default
    Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
