package com.example.library_management.dto.response;

import java.util.List;

public record ChatReply(
        String reply,
        List<PendingActionResponse> pendingActions) {
}