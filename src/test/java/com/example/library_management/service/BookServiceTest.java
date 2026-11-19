package com.example.library_management.service;

import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.request.CategoryRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.dto.response.CategoryResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.entity.Category;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.BookMapper;
import com.example.library_management.mapper.CategoryMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.CategoryRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookServiceTest {

    @Mock
    BookRepository bookRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ReservationRepository reservationRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    ReservationService reservationService;
    @Mock
    FileStorageService fileStorageService;

    BookMapper bookMapper = Mappers.getMapper(BookMapper.class);

    BookService bookService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bookMapper, "categoryMapper", Mappers.getMapper(CategoryMapper.class));
        bookService = new BookService(bookRepository, categoryRepository, reservationRepository,
                userRepository, bookMapper, reservationService, fileStorageService);
    }

    private BookRequest.BookRequestBuilder validRequest() {
        return BookRequest.builder()
                .title("Clean Code")
                .isbn("978-0132350884")
                .author("Robert C. Martin")
                .totalCopies(3)
                .categoryId(1L);
    }

//    CREATE BOOK
    @Test
    void createBook_success() {
        Category category = Category.builder().id(1L).name("Programming").build();
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });

        // isbn co khoang trang thua o 2 dau
        BookResponse response = bookService.createBook(validRequest().isbn("  978-0132350884  ").build());

        assertEquals(10L, response.getId());
        assertEquals("978-0132350884", response.getIsbn());
        assertEquals(3, response.getTotalCopies());
        assertEquals(3, response.getAvailableCopies());
        assertEquals(BookStatus.AVAILABLE, response.getStatus());
        assertEquals("Programming", response.getCategory().getName());
        assertEquals(0, response.getReservedCopies());
    }

    @Test
    void createBook_zeroCopies() {
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.builder().id(1L).name("Programming").build()));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        BookResponse response = bookService.createBook(validRequest().totalCopies(0).build());

        assertEquals(0, response.getAvailableCopies());
        assertEquals(BookStatus.OUT_OF_STOCK, response.getStatus());
    }

    @Test
    void createBook_isbnExisted() {
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> bookService.createBook(validRequest().build()));

        assertEquals(ErrorCode.BOOK_ISBN_EXISTED, ex.getErrorCode());
        verifyNoInteractions(categoryRepository);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void createBook_categoryNotFound() {
        when(bookRepository.existsByIsbn("978-0132350884")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> bookService.createBook(validRequest().build()));

        assertEquals(ErrorCode.CATEGORY_NOT_EXISTED, ex.getErrorCode());
        verify(bookRepository, never()).save(any());
    }

//    DELETE
    @Test
    void deleteBook_notFound() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> bookService.deleteBook(99L));

        assertEquals(ErrorCode.BOOK_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void deleteBook_hasBorrowRecords() {
        Book book = Book.builder().id(1L).borrowRecords(Set.of(new BorrowRecord())).build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        AppException ex = assertThrows(AppException.class, () -> bookService.deleteBook(1L));

        assertEquals(ErrorCode.BOOK_HAS_BORROW_RECORDS, ex.getErrorCode());
        verify(bookRepository, never()).delete(any());
    }

    @Test
    void deleteBook_hasReservations() {
        Book book = Book.builder().id(1L).build(); // khong co borrow record
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(reservationRepository.existsByBookIdAndStatusIn(eq(1L), any())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> bookService.deleteBook(1L));

        assertEquals(ErrorCode.BOOK_HAS_ACTIVE_RESERVATIONS, ex.getErrorCode());
        verify(bookRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService); // chua xoa sach thi khong duoc xoa anh bia
    }

    @Test
    void deleteBook_success() {
        Book book = Book.builder().id(1L).coverImageUrl("/uploads/books/cover.png").build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(reservationRepository.existsByBookIdAndStatusIn(eq(1L), any())).thenReturn(false);

        bookService.deleteBook(1L);

        verify(bookRepository).delete(book);
        verify(fileStorageService).deleteAfterCommit("/uploads/books/cover.png");
    }
}
