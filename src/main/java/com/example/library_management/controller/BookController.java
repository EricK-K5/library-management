package com.example.library_management.controller;

import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    @PreAuthorize("hasAuthority('book:create')")
    public ApiResponse<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        return ApiResponse.<BookResponse>builder()
                .result(bookService.createBook(request))
                .build();
    }

    @GetMapping
    public ApiResponse<Page<BookResponse>> getAllBooks(
            @PageableDefault(size = 10, sort = "title") Pageable pageable) {
        return ApiResponse.<Page<BookResponse>>builder()
                .result(bookService.getAllBooks(pageable))
                .build();
    }

    @GetMapping("/{id}")
//    @PreAuthorize("hasAuthority('book:read')")
    public ApiResponse<BookResponse> getBookById(@PathVariable Long id) {
        return ApiResponse.<BookResponse>builder()
                .result(bookService.getBookById(id))
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('book:update')")
    public ApiResponse<BookResponse> updateBook(
            @PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return ApiResponse.<BookResponse>builder()
                .result(bookService.updateBook(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('book:delete')")
    public ApiResponse<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ApiResponse.<Void>builder().build();
    }
    
//    SEARCH BY CATEGORY
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('book:read')")
    public ApiResponse<Page<BookResponse>> searchBooks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 10, sort = "title") Pageable pageable) {

        return ApiResponse.<Page<BookResponse>>builder()
                .result(bookService.searchBooks(keyword, categoryId, pageable))
                .build();
    }
}
