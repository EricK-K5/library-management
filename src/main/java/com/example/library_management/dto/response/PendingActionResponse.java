package com.example.library_management.dto.response;

import java.time.Instant;

public record PendingActionResponse(
        String actionId,
        String type,
        Long bookId,
        String bookTitle,
        Instant expiresAt,
        long expiresInSeconds) {
}