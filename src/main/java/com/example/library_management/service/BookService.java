package com.example.library_management.service;

import ch.qos.logback.core.util.StringUtil;
import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.Category;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.BookMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class BookService {
    BookRepository bookRepository;
    CategoryRepository categoryRepository;
    BookMapper bookMapper;

//    CREATE BOOK
    @Transactional
    public BookResponse createBook(BookRequest request) {
        if(StringUtils.hasText(request.getIsbn()) && bookRepository.existsByIsbn(request.getIsbn())) {
            throw new AppException(ErrorCode.BOOK_ISBN_EXISTED);
        }
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_EXISTED));

        Book book = bookMapper.toBook(request);
        book.setCategory(category);
        book.setStatus(computeStatus(book.getAvailableCopies()));

        return bookMapper.toBookResponse(bookRepository.save(book));
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll()
                .stream()
                .map(bookMapper::toBookResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        return bookMapper.toBookResponse(book);
    }

    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        // Neu doi isbn thi kiem tra trung, tru truong hop giu nguyen isbn cu
        if (StringUtils.hasText(request.getIsbn())
                && !request.getIsbn().equals(book.getIsbn())
                && bookRepository.existsByIsbn(request.getIsbn())) {
            throw new AppException(ErrorCode.BOOK_ISBN_EXISTED);
        }

        if (!request.getCategoryId().equals(book.getCategory().getId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_EXISTED));
            book.setCategory(category);
        }

        int borrowedCopies = book.getTotalCopies() - book.getAvailableCopies();
        if (request.getTotalCopies() < borrowedCopies) {
            throw new AppException(ErrorCode.TOTAL_COPIES_LESS_THAN_BORROWED);
        }

        bookMapper.updateBook(book, request);
        // dieu chinh available theo delta cua total (khong dung mapper vi mapper dang ignore field nay)
        book.setAvailableCopies(request.getTotalCopies() - borrowedCopies);
        book.setStatus(computeStatus(book.getAvailableCopies()));

        return bookMapper.toBookResponse(bookRepository.save(book));
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        if (!book.getBorrowRecords().isEmpty()) {
            throw new AppException(ErrorCode.BOOK_HAS_BORROW_RECORDS);
        }

        bookRepository.delete(book);
    }

    private BookStatus computeStatus(int availableCopies) {
        return availableCopies > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> searchBooks(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            // keyword rong thi tra ve tat ca, tranh loi query rong vo nghia
            return getAllBooks();
        }
        return bookRepository.searchBooks(keyword.trim())
                .stream()
                .map(bookMapper::toBookResponse)
                .toList();
    }
}
