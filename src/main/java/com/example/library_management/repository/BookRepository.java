package com.example.library_management.repository;

import com.example.library_management.entity.Book;
import com.example.library_management.entity.Category;
import com.example.library_management.enums.BookStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);

    // Pessimistic Write Lock: dung khi muon sach de tranh 2 user cung muon vuot qua so luong con lai
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    // Sách còn có thể mượn được
//    List<Book> findByAvailableCopiesGreaterThan(Integer minCopies);

    //    SEARCH BOOK
    @Query("""
            SELECT b FROM Book b
            JOIN b.category c
            WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Book> searchBooks(@Param("keyword") String keyword);
}
