package com.example.library_management.dto.response;

import java.time.Instant;

/**
 * Một thao tác đang chờ user bấm nút xác nhận (frontend dùng để hiện card + nút).
 */
public record PendingActionResponse(
        String actionId,
        String type,
        Long bookId,
        String bookTitle,
        Instant expiresAt,
        long expiresInSeconds) {
}