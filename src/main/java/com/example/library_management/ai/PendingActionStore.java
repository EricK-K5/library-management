package com.example.library_management.ai;

import com.example.library_management.dto.response.PendingActionResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Lưu các thao tác AI "đề xuất" nhưng CHƯA thực hiện, chờ user bấm nút xác nhận.
 * <p>
 * Điểm mấu chốt: model chỉ có thể TẠO pending action (qua tool). Việc consume (xác nhận) chỉ
 * xảy ra từ endpoint HTTP riêng của user, nên model không thể tự xác nhận thay user.
 * <p>
 * Lưu in-memory: restart server là mất (user chỉ cần nhờ AI tạo lại), và không dùng được khi
 * chạy nhiều instance. Khi cần scale thì thay bằng Redis/DB, giữ nguyên các method public.
 */
@Component
public class PendingActionStore {

    public enum ActionType {
        RESERVE_BOOK
    }

    public record PendingAction(String id,
                                String username,
                                ActionType type,
                                Long bookId,
                                String bookTitle,
                                Instant createdAt,
                                Instant expiresAt) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private static final int MAX_PENDING_PER_USER = 5;

    private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public PendingActionStore(@Value("${app.ai.pending-action-ttl-minutes:5}") long ttlMinutes) {
        this(Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
    }

    // Dùng cho test (truyền Clock giả)
    PendingActionStore(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Tạo pending action. Idempotent: nếu user đã có action cùng (type, bookId) còn hạn thì trả lại
     * chính action đó (phòng trường hợp model gọi tool 2 lần). Mỗi user giữ tối đa 5 action,
     * vượt thì xoá cái cũ nhất.
     */
    public synchronized PendingAction create(String username, ActionType type, Long bookId, String bookTitle) {
        Instant now = clock.instant();
        purgeExpired(now);

        for (PendingAction existing : actions.values()) {
            if (existing.username().equals(username)
                    && existing.type() == type
                    && Objects.equals(existing.bookId(), bookId)) {
                return existing;
            }
        }

        List<PendingAction> mine = actions.values().stream()
                .filter(a -> a.username().equals(username))
                .sorted(Comparator.comparing(PendingAction::createdAt))
                .collect(Collectors.toCollection(ArrayList::new));
        while (mine.size() >= MAX_PENDING_PER_USER) {
            actions.remove(mine.remove(0).id());
        }

        PendingAction action = new PendingAction(
                UUID.randomUUID().toString(), username, type, bookId, bookTitle, now, now.plus(ttl));
        actions.put(action.id(), action);
        return action;
    }

    /**
     * Xem action (kiểm tra đúng user + còn hạn) nhưng KHÔNG xoá. Dùng để kiểm tra quyền trước khi consume,
     * tránh làm mất action của user chỉ vì bị từ chối quyền.
     */
    public PendingAction peek(String actionId, String username) {
        return findValid(actionId, username);
    }

    /**
     * Xác nhận: dùng được đúng 1 lần. Không tồn tại / hết hạn / không thuộc user này đều ném CÙNG một
     * lỗi, để không lộ việc action của người khác có tồn tại.
     */
    public PendingAction consume(String actionId, String username) {
        PendingAction action = findValid(actionId, username);
        // remove(key, value) là atomic: nếu request khác đã consume trước thì trả về false
        if (!actions.remove(actionId, action)) {
            throw notFound();
        }
        return action;
    }

    /** Huỷ: kiểm tra giống consume rồi xoá. */
    public void cancel(String actionId, String username) {
        PendingAction action = findValid(actionId, username);
        if (!actions.remove(actionId, action)) {
            throw notFound();
        }
    }

    public PendingActionResponse toResponse(PendingAction action) {
        long secondsLeft = Math.max(0, Duration.between(clock.instant(), action.expiresAt()).toSeconds());
        return new PendingActionResponse(
                action.id(),
                action.type().name(),
                action.bookId(),
                action.bookTitle(),
                action.expiresAt(),
                secondsLeft);
    }

    @Scheduled(fixedDelay = 60_000)
    public void purgeExpired() {
        purgeExpired(clock.instant());
    }

    private void purgeExpired(Instant now) {
        actions.values().removeIf(a -> a.isExpired(now));
    }

    private PendingAction findValid(String actionId, String username) {
        PendingAction action = actionId == null ? null : actions.get(actionId);
        if (action == null
                || action.isExpired(clock.instant())
                || !action.username().equals(username)) {
            throw notFound();
        }
        return action;
    }

    private AppException notFound() {
        return new AppException(ErrorCode.PENDING_ACTION_NOT_FOUND);
    }
}