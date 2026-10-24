package com.example.library_management.dto.response;

import java.util.List;

/**
 * Response của /chat và /chat-with-image: câu trả lời của AI + các thao tác đang chờ user xác nhận.
 */
public record ChatReply(
        String reply,
        List<PendingActionResponse> pendingActions) {
}