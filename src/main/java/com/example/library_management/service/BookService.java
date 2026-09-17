package com.example.library_management.service;

import ch.qos.logback.core.util.StringUtil;
import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.Category;
import com.example.library_management.entity.Reservation;
import com.example.library_management.entity.User;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.mapper.BookMapper;
import com.example.library_management.repository.BookRepository;
import com.example.library_management.repository.CategoryRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class BookService {
    private static final Set<ReservationStatus> ACTIVE_STATUSES = Set.of(
            ReservationStatus.PENDING, ReservationStatus.ACCEPTED);

    BookRepository bookRepository;
    CategoryRepository categoryRepository;
    ReservationRepository reservationRepository;
    UserRepository userRepository;
    BookMapper bookMapper;

    // Gan them so ban dang duoc giu cho nguoi da dat (ACCEPTED), tinh dong khong luu cot rieng
    private BookResponse enrich(BookResponse response, Long bookId) {
        response.setReservedCopies((int) reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.ACCEPTED));
        return response;
    }

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

        return enrich(bookMapper.toBookResponse(bookRepository.save(book)), book.getId());
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll()
                .stream()
                .map(book -> enrich(bookMapper.toBookResponse(book), book.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        BookResponse response = enrich(bookMapper.toBookResponse(book), book.getId());

        // Gan them tinh trang dat sach cua CHINH nguoi dang xem (dung cho trang chi tiet 1 sach)
        String currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            reservationRepository
                    .findByUserIdAndBookIdAndStatusIn(currentUserId, book.getId(), ACTIVE_STATUSES)
                    .stream()
                    .findFirst()
                    .ifPresent(reservation -> {
                        response.setMyReservationId(reservation.getId());
                        response.setMyReservationStatus(reservation.getStatus());
                        response.setMyReservationExpiryDate(reservation.getExpiryDate());
                        if (reservation.getStatus() == ReservationStatus.PENDING) {
                            List<Reservation> queue = reservationRepository
                                    .findByBookIdAndStatusOrderByCreatedAtAscIdAsc(book.getId(), ReservationStatus.PENDING);
                            for (int i = 0; i < queue.size(); i++) {
                                if (queue.get(i).getId().equals(reservation.getId())) {
                                    response.setMyReservationQueuePosition(i + 1);
                                    break;
                                }
                            }
                        }
                    });
        }

        return response;
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

        return enrich(bookMapper.toBookResponse(bookRepository.save(book)), book.getId());
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

    // Lay id user hien tai neu da dang nhap hop le, tra ve null neu khong xac dinh duoc (khong throw
    // vi day chi la thong tin bo sung, khong bat buoc de xem duoc chi tiet sach)
    private String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BookResponse> searchBooks(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            // keyword rong thi tra ve tat ca, tranh loi query rong vo nghia
            return getAllBooks();
        }
        return bookRepository.searchBooks(keyword.trim())
                .stream()
                .map(book -> enrich(bookMapper.toBookResponse(book), book.getId()))
                .toList();
    }
}
