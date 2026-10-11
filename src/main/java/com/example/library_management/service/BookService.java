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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
    ReservationService reservationService;
    FileStorageService fileStorageService;

    // Gan them so ban dang duoc giu cho nguoi da dat (ACCEPTED)
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
    public Page<BookResponse> getAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable)
                .map(book -> enrich(bookMapper.toBookResponse(book), book.getId()));
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

    private BookStatus computeStatus(int availableCopies) {
        return availableCopies > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK;
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

        int oldAvailableCopies = book.getAvailableCopies();

        bookMapper.updateBook(book, request);
        // dieu chinh available theo delta cua total (khong dung mapper vi mapper dang ignore field nay)
        int newAvailableCopies = request.getTotalCopies() - borrowedCopies;
        book.setAvailableCopies(newAvailableCopies);
        book.setStatus(computeStatus(newAvailableCopies));

        Book saved = bookRepository.save(book);

        // Neu so ban co the muon tang len (vd them ban moi), tu dong day hang doi PENDING len ACCEPTED
        // truoc khi cho phep bat ky ai khac muon truc tiep cac ban vua them.
        if (newAvailableCopies > oldAvailableCopies) {
            reservationService.promotePendingQueue(saved.getId());
            saved = bookRepository.findById(saved.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));
        }

        return enrich(bookMapper.toBookResponse(saved), saved.getId());
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        if (!book.getBorrowRecords().isEmpty()) {
            throw new AppException(ErrorCode.BOOK_HAS_BORROW_RECORDS);
        }

        // Book.reservations dang cascade = ALL: neu khong chan o day, xoa sach se xoa "am tham" ca
        // reservation PENDING/ACCEPTED cua member khac, ho mat cho ma khong duoc bao/huy dung cach.
        if (reservationRepository.existsByBookIdAndStatusIn(id, ACTIVE_STATUSES)) {
            throw new AppException(ErrorCode.BOOK_HAS_ACTIVE_RESERVATIONS);
        }

        bookRepository.delete(book);
        // Xoa luon anh bia (chi chay sau khi commit thanh cong)
        fileStorageService.deleteAfterCommit(book.getCoverImageUrl());
    }

    // UPLOAD / DOI ANH BIA
    @Transactional
    public BookResponse updateCover(Long id, MultipartFile file) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        String oldUrl = book.getCoverImageUrl();
        book.setCoverImageUrl(fileStorageService.store(
                file, FileStorageService.BOOK_FOLDER, FileStorageService.COVER_MAX_BYTES));
        Book saved = bookRepository.save(book);

        fileStorageService.deleteAfterCommit(oldUrl);
        return enrich(bookMapper.toBookResponse(saved), saved.getId());
    }

    // XOA ANH BIA
    @Transactional
    public BookResponse deleteCover(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOK_NOT_EXISTED));

        String oldUrl = book.getCoverImageUrl();
        book.setCoverImageUrl(null);
        Book saved = bookRepository.save(book);

        fileStorageService.deleteAfterCommit(oldUrl);
        return enrich(bookMapper.toBookResponse(saved), saved.getId());
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
    public Page<BookResponse> searchBooks(String keyword, Pageable pageable) {

        if (!StringUtils.hasText(keyword)) {
            return getAllBooks(pageable);
        }

        return bookRepository.searchBooks(keyword.trim(), pageable)
                .map(book -> enrich(
                        bookMapper.toBookResponse(book),
                        book.getId()
                ));
    }

    //    SEARCH BOOK BY CATEGORIES
    @Transactional(readOnly = true)
    public Page<BookResponse> searchBooks(String keyword, Long categoryId, Pageable pageable) {

        if (categoryId == null) {
            // hành vi cũ, giữ nguyên
            if (!StringUtils.hasText(keyword)) {
                return getAllBooks(pageable);
            }
            return bookRepository.searchBooks(keyword.trim(), pageable)
                    .map(book -> enrich(bookMapper.toBookResponse(book), book.getId()));
        }

        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        return bookRepository.searchBooksByCategory(categoryId, normalizedKeyword, pageable)
                .map(book -> enrich(bookMapper.toBookResponse(book), book.getId()));
    }
}
