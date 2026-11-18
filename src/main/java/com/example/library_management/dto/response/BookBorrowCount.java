package com.example.library_management.dto.response;

public record BookBorrowCount(Long bookId, String title, String categoryName, Long borrowCount) {
}
