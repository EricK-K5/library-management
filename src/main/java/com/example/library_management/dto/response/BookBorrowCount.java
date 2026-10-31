package com.example.library_management.dto.response;

/** 1 dong GROUP BY book cua Most Borrowed Books Report (dung cho JPQL constructor expression). */
public record BookBorrowCount(Long bookId, String title, String categoryName, Long borrowCount) {
}
