package com.example.library_management.ai;

import com.example.library_management.ai.PendingActionStore.ActionType;
import com.example.library_management.ai.PendingActionStore.PendingAction;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PendingActionStoreTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    MutableClock clock;
    PendingActionStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        store = new PendingActionStore(Duration.ofMinutes(5), clock);
    }

    private void assertNotFound(Runnable r) {
        AppException ex = assertThrows(AppException.class, r::run);
        assertEquals(ErrorCode.PENDING_ACTION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void create_isIdempotentForSameUserAndBook() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        PendingAction b = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertEquals(a.id(), b.id());
    }

    @Test
    void create_differentBookOrUser_createsNewAction() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        PendingAction b = store.create("khoa", ActionType.RESERVE_BOOK, 2L, "Java");
        PendingAction c = store.create("bob", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertNotEquals(a.id(), b.id());
        assertNotEquals(a.id(), c.id());
    }

    @Test
    void consume_worksExactlyOnce() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertEquals(a, store.consume(a.id(), "khoa"));
        assertNotFound(() -> store.consume(a.id(), "khoa"));
    }

    @Test
    void consume_wrongUser_fails_andDoesNotDestroyOwnersAction() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertNotFound(() -> store.consume(a.id(), "bob"));
        // khoa vẫn xác nhận được
        assertEquals(a, store.consume(a.id(), "khoa"));
    }

    @Test
    void consume_unknownOrNullId_fails() {
        assertNotFound(() -> store.consume("khong-ton-tai", "khoa"));
        assertNotFound(() -> store.consume(null, "khoa"));
    }

    @Test
    void consume_afterExpiry_fails() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        clock.advance(Duration.ofMinutes(5));
        assertNotFound(() -> store.consume(a.id(), "khoa"));
    }

    @Test
    void cancel_removesAction_andChecksOwner() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertNotFound(() -> store.cancel(a.id(), "bob"));
        store.cancel(a.id(), "khoa");
        assertNotFound(() -> store.consume(a.id(), "khoa"));
    }

    @Test
    void create_keepsAtMostFivePerUser_dropsOldest() {
        PendingAction first = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "B1");
        for (long i = 2; i <= 6; i++) {
            clock.advance(Duration.ofSeconds(1));
            store.create("khoa", ActionType.RESERVE_BOOK, i, "B" + i);
        }
        // đã tạo 6 -> cái đầu tiên bị đẩy ra
        assertNotFound(() -> store.consume(first.id(), "khoa"));
    }

    @Test
    void toResponse_reportsSecondsLeft() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        clock.advance(Duration.ofMinutes(2));
        assertEquals(180, store.toResponse(a).expiresInSeconds());
        clock.advance(Duration.ofMinutes(10));
        assertEquals(0, store.toResponse(a).expiresInSeconds());
    }

    @Test
    void peek_doesNotConsume_butChecksOwnerAndExpiry() {
        PendingAction a = store.create("khoa", ActionType.RESERVE_BOOK, 1L, "Clean Code");
        assertEquals(a, store.peek(a.id(), "khoa"));
        assertEquals(a, store.peek(a.id(), "khoa"));          // xem nhiều lần vẫn còn
        assertNotFound(() -> store.peek(a.id(), "bob"));       // sai user
        assertEquals(a, store.consume(a.id(), "khoa"));       // vẫn consume được sau khi peek
        PendingAction b = store.create("khoa", ActionType.RESERVE_BOOK, 2L, "Java");
        clock.advance(Duration.ofMinutes(5));
        assertNotFound(() -> store.peek(b.id(), "khoa"));     // hết hạn
    }
}