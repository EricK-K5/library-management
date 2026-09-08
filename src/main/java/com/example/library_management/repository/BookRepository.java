package com.example.library_management.repository;

import com.example.library_management.entity.Book;
import com.example.library_management.enums.BookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    List<Book> findByStatus(BookStatus status);

    List<Book> findByCategoryId(String categoryId);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Book> findByAuthorContainingIgnoreCase(String author);

    // Sách còn có thể mượn được
    List<Book> findByAvailableCopiesGreaterThan(Integer minCopies);
}
