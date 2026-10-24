package com.example.library_management.service;

import com.example.library_management.ai.PendingActionStore;
import com.example.library_management.ai.PendingActionStore.ActionType;
import com.example.library_management.ai.PendingActionStore.PendingAction;
import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.dto.response.ChatActionResult;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Xử lý nút "Xác nhận" / "Huỷ" của user cho các thao tác AI đề xuất (hiện chỉ có đặt trước sách).
 * Được gọi từ HTTP request riêng của user (có JWT), KHÔNG đi qua model.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class ChatActionService {

    PendingActionStore pendingActionStore;
    ReservationService reservationService;

    /**
     * Xác nhận một thao tác đang chờ.
     * <p>
     * Thứ tự: (1) peek: đúng user + còn hạn -> (2) kiểm tra quyền theo loại action -> (3) consume (dùng 1 lần)
     * -> (4) thực hiện. Kiểm tra quyền TRƯỚC khi consume để bị từ chối quyền thì action không bị mất.
     * Action đã bị consume kể cả khi bước (4) ném lỗi nghiệp vụ (còn phí phạt, quá hạn mức...): user phải
     * nhờ AI tạo yêu cầu mới, đảm bảo 1 action không bao giờ chạy 2 lần.
     */
    public ChatActionResult confirm(String actionId) {
        String username = currentUsername();

        PendingAction pending = pendingActionStore.peek(actionId, username);
        requireAuthority(requiredAuthority(pending.type()));

        PendingAction action = pendingActionStore.consume(actionId, username);

        return switch (action.type()) {
            case RESERVE_BOOK -> toResult(reservationService.createReservation(
                    ReservationRequest.builder().bookId(action.bookId()).build()));
        };
    }

    public void cancel(String actionId) {
        pendingActionStore.cancel(actionId, currentUsername());
    }

    // Quyền cần có để xác nhận từng loại action (khớp với @PreAuthorize ở ReservationController)
    private String requiredAuthority(ActionType type) {
        return switch (type) {
            case RESERVE_BOOK -> "reservation:create";
        };
    }

    private void requireAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
        if (!allowed) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private ChatActionResult toResult(ReservationResponse r) {
        return new ChatActionResult(
                ActionType.RESERVE_BOOK.name(),
                r.getId(),
                titleOf(r.getBook()),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getExpiryDate(),
                r.getQueuePosition());
    }

    private String titleOf(BookResponse book) {
        return book != null ? book.getTitle() : null;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return auth.getName();
    }
}