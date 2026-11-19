package com.example.library_management.repository;

import com.example.library_management.entity.Book;
import com.example.library_management.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Kiem tra 2 cau JPQL tu viet (searchBooks, searchBooksByCategory) tren H2 that.
// Mock khong bat duoc loi o day: sai ten field, sai OR/AND, quen LOWER... chi lo ra khi chay query that.
@DataJpaTest
@ActiveProfiles("test")
class BookRepositoryTest {

    @Autowired BookRepository bookRepository;
    @Autowired CategoryRepository categoryRepository;

    final Pageable page = PageRequest.of(0, 10);

    Category programming;
    Category scifi;

    @BeforeEach
    void setUp() {
        programming = categoryRepository.save(Category.builder().name("Programming").build());
        scifi = categoryRepository.save(Category.builder().name("Science Fiction").build());

        save("Clean Code", "Robert Martin", "isbn-1", programming);
        save("Clean Architecture", "Robert Martin", "isbn-2", programming);
        save("Dune", "Frank Herbert", "isbn-3", scifi);
        // sach thuoc scifi nhung title cung chua "clean" -> dung de kiem tra loc theo category
        save("Clean Slate", "Someone", "isbn-4", scifi);
    }

    private void save(String title, String author, String isbn, Category category) {
        bookRepository.save(Book.builder().title(title).author(author).isbn(isbn).category(category).build());
    }

    private List<String> titles(org.springframework.data.domain.Page<Book> result) {
        return result.getContent().stream().map(Book::getTitle).sorted().toList();
    }

    // SEARCH BOOKS
    @Test
    void searchBooks_matchesTitleAuthorOrCategoryName_ignoringCase() {
        // theo title, khong phan biet hoa thuong
        assertEquals(List.of("Clean Architecture", "Clean Code", "Clean Slate"),
                titles(bookRepository.searchBooks("CLEAN", page)));

        // theo author
        assertEquals(List.of("Dune"),
                titles(bookRepository.searchBooks("herbert", page)));

        // theo ten category (JOIN b.category): "science" chi xuat hien trong ten category cua Dune va Clean Slate
        assertEquals(List.of("Clean Slate", "Dune"),
                titles(bookRepository.searchBooks("science", page)));

        // khong khop gi
        assertTrue(bookRepository.searchBooks("zzz", page).isEmpty());
    }

//    SEARCH BOOKS BY CATEGORY
    @Test
    void searchBooksByCategory_filtersByCategoryAndOptionalKeyword() {
        // keyword rong -> lay het sach cua category (nhanh ":keyword = ''")
        assertEquals(List.of("Clean Architecture", "Clean Code"),
                titles(bookRepository.searchBooksByCategory(programming.getId(), "", page)));

        // keyword + category: "clean" khop 3 sach nhung chi 1 sach thuoc scifi -> chung to la AND, khong phai OR
        assertEquals(List.of("Clean Slate"),
                titles(bookRepository.searchBooksByCategory(scifi.getId(), "clean", page)));

        // keyword khop theo author trong dung category
        assertEquals(List.of("Dune"),
                titles(bookRepository.searchBooksByCategory(scifi.getId(), "herbert", page)));

        // keyword khop sach o category khac -> khong tra ve
        assertTrue(bookRepository.searchBooksByCategory(programming.getId(), "dune", page).isEmpty());
    }
}
