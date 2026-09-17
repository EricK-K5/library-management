package com.example.library_management.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Size(max = 100)
    @Column(nullable = false, unique = true)
    String name;

    @Column(length = 255)
    String description;

    @OneToMany(mappedBy = "category")
    @Builder.Default
    Set<Book> books = new HashSet<>();
}
