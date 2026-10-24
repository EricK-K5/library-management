package com.example.library_management.ai;

import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.dto.response.PendingActionResponse;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.BookService;
import com.example.library_management.service.FineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Các "công cụ" mà AI được phép gọi.
 * <p>
 * Lưu ý quan trọng: tool gọi THẲNG vào service nên sẽ bỏ qua @PreAuthorize ở controller.
 * Vì vậy mỗi tool tự kiểm tra authority trong execute(...).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryTools {

    private static final int MAX_SEARCH_RESULTS = 5;

    /** Key trong ToolContext: ChatService truyền vào 1 List để tool gom các pending action của request này. */
    public static final String PENDING_ACTIONS_KEY = "pendingActions";

    /**
     * WHITELIST: AI chỉ được dùng đúng bộ quyền của role MEMBER (giống DataSeedingService).
     * Dù người đang chat là ADMIN/LIBRARIAN thì AI vẫn chỉ làm được việc của member.
     * Nếu sau này lỡ viết tool cần quyền ngoài danh sách này (fine:manage, borrow:return...)
     * thì execute(...) sẽ chặn và ghi log lỗi.
     */
    private static final Set<String> AI_ALLOWED_AUTHORITIES = Set.of(
            "book:read",
            "borrow:read", "borrow:renew",
            "reservation:read", "reservation:create", "reservation:cancel",
            "fine:read", "fine:pay"
    );

    private final BookService bookService;
    private final FineService fineService;
    private final PendingActionStore pendingActionStore;

    // ======================================================================
    // DTO rút gọn trả cho model (đừng trả nguyên BookResponse: dư coverImageUrl,
    // createdAt... tốn token và dễ làm model rối)
    // ======================================================================

    public record ToolResult<T>(boolean success, String message, T data) {
        static <T> ToolResult<T> ok(T data) {
            return new ToolResult<>(true, "OK", data);
        }

        static <T> ToolResult<T> fail(String message) {
            return new ToolResult<>(false, message, null);
        }
    }

    public record BookSummary(Long id, String title, String author, String category,
                              Integer availableCopies, String status) {
    }

    public record BookDetail(Long id, String title, String author, String publisher, Integer publishYear,
                             String language, String description, String category, Integer totalCopies,
                             Integer availableCopies, String status, String shelfLocation, BigDecimal price,
                             String myReservationStatus, Integer myReservationQueuePosition) {
    }

    public record FineInfo(Long id, String bookTitle, BigDecimal amount, String reason,
                           String status, LocalDate issuedDate) {
    }

    public record MyFines(BigDecimal totalUnpaid, List<FineInfo> fines) {
    }

    public record ConfirmationRequestInfo(String status, String bookTitle, long expiresInSeconds, String note) {
    }

    // ======================================================================
    // TOOLS
    // ======================================================================

    // GET_BOOK
    @Tool(description = """
            Tìm sách trong thư viện theo từ khóa (tên sách, tác giả hoặc thể loại).
            Trả về tối đa 5 cuốn kèm id, số bản còn lại và trạng thái.
            Dùng khi người dùng muốn tìm/xem danh sách sách.""")
    public ToolResult<List<BookSummary>> searchBooks(
            @ToolParam(description = "Từ khóa tìm kiếm: tên sách, tác giả hoặc thể loại. Ví dụ: 'Java', 'Clean Code'")
            String keyword) {

        return execute("book:read", () -> bookService
                .searchBooks(keyword, PageRequest.of(0, MAX_SEARCH_RESULTS))
                .map(this::toSummary)
                .getContent());
    }

    // BOOK_DESCRIPTION
    @Tool(description = """
            Lấy thông tin chi tiết và mô tả của MỘT cuốn sách theo id.
            Chỉ gọi khi đã có id từ kết quả searchBooks; không được tự đoán id.""")
    public ToolResult<BookDetail> getBookDetail(
            @ToolParam(description = "Id của cuốn sách, lấy từ kết quả searchBooks")
            Long bookId) {

        return execute("book:read", () -> toDetail(bookService.getBookById(bookId)));
    }

    // VIEW_FINE
    @Tool(description = """
            Xem các khoản phí phạt của CHÍNH người dùng đang chat (không xem được của người khác).
            Trả về tổng tiền chưa đóng và danh sách từng khoản.""")
    public ToolResult<MyFines> getMyFines() {
        return execute("fine:read", () -> {
            List<FineInfo> fines = fineService.getMyFines().stream().map(this::toFineInfo).toList();
            BigDecimal totalUnpaid = fines.stream()
                    .filter(f -> "UNPAID".equals(f.status()) && f.amount() != null)
                    .map(FineInfo::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MyFines(totalUnpaid, fines);
        });
    }

    // RESERVE_BOOK: KHÔNG đặt ngay. Chỉ tạo yêu cầu chờ, user phải bấm nút xác nhận trên giao diện
    // (endpoint /chat/actions/{id}/confirm). Model không có cách nào tự xác nhận.
    // AI KHÔNG có tool mượn sách: user tự mượn trên hệ thống.

    @Tool(description = """
            Tạo YÊU CẦU đặt trước một cuốn sách cho người dùng đang chat.
            Tool này KHÔNG đặt trước ngay: nó chỉ tạo yêu cầu chờ, người dùng phải bấm nút "Xác nhận đặt trước"
            trên giao diện thì mới được đặt. Không bao giờ được nói là đã đặt trước thành công.
            Chỉ gọi khi đã xác định đúng một cuốn sách (id lấy từ searchBooks).""")
    public ToolResult<ConfirmationRequestInfo> requestReserveBook(
            @ToolParam(description = "Id của cuốn sách cần đặt trước, lấy từ kết quả searchBooks")
            Long bookId,
            ToolContext toolContext) {

        return requestConfirmation("reservation:create", PendingActionStore.ActionType.RESERVE_BOOK, bookId, toolContext,
                "Chưa đặt trước. Người dùng phải bấm nút Xác nhận đặt trước trên giao diện. "
                        + "Không được thông báo đã đặt trước thành công.");
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    /**
     * Chạy 1 tool: kiểm tra quyền -> gọi service -> bọc kết quả.
     * Bắt AppException để trả lý do lỗi (vd "User has unpaid fine") về cho model giải thích,
     * thay vì để exception nổ ra ngoài làm hỏng cả cuộc chat.
     */
    private <T> ToolResult<T> execute(String requiredAuthority, Supplier<T> action) {
        try {
            // Fail-closed: tool nào quên khai báo quyền thì bị chặn, không âm thầm bỏ qua kiểm tra
            if (requiredAuthority == null) {
                log.error("Tool được gọi mà không khai báo requiredAuthority -> đã chặn");
                return ToolResult.fail("Chức năng này chưa được cấu hình quyền nên AI không thực hiện được.");
            }
            if (!AI_ALLOWED_AUTHORITIES.contains(requiredAuthority)) {
                log.error("Tool yêu cầu quyền '{}' nằm ngoài phạm vi MEMBER của AI -> đã chặn", requiredAuthority);
                return ToolResult.fail("Chức năng này không được AI hỗ trợ. Vui lòng thực hiện trực tiếp trên hệ thống.");
            }
            if (!hasAuthority(requiredAuthority)) {
                return ToolResult.fail("Tài khoản hiện tại không có quyền thực hiện thao tác này.");
            }
            return ToolResult.ok(action.get());
        } catch (AppException e) {
            log.warn("Tool failed: {}", e.getMessage());
            return ToolResult.fail(e.getMessage());
        }
    }

    /**
     * Dùng chung cho các thao tác ghi dữ liệu: kiểm tra quyền + sách, rồi chỉ TẠO pending action.
     * Việc thực hiện thật nằm ở ChatActionService.confirm (sau khi user bấm nút).
     */
    private ToolResult<ConfirmationRequestInfo> requestConfirmation(String requiredAuthority,
                                                                    PendingActionStore.ActionType type,
                                                                    Long bookId,
                                                                    ToolContext toolContext,
                                                                    String note) {
        return execute(requiredAuthority, () -> {
            BookResponse book = bookService.getBookById(bookId);
            if (book.getStatus() == BookStatus.DISCONTINUED) {
                throw new AppException(ErrorCode.BOOK_DISCONTINUED);
            }
            // Cố ý KHÔNG kiểm tra hết sách ở bước này: user đang có reservation ACCEPTED vẫn mượn được
            // dù availableCopies = 0, và đặt trước khi hết sách thì được xếp hàng chờ.
            // Điều kiện nghiệp vụ đầy đủ do service kiểm tra lúc user xác nhận.

            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            PendingActionStore.PendingAction action =
                    pendingActionStore.create(username, type, book.getId(), book.getTitle());
            PendingActionResponse response = pendingActionStore.toResponse(action);

            collectForFrontend(toolContext, response);

            return new ConfirmationRequestInfo(
                    "AWAITING_USER_CONFIRMATION", book.getTitle(), response.expiresInSeconds(), note);
        });
    }

    // Đưa pending action vào List mà ChatService truyền qua ToolContext (không qua model)
    private void collectForFrontend(ToolContext toolContext, PendingActionResponse response) {
        if (toolContext == null || toolContext.getContext() == null) {
            return;
        }
        Object collector = toolContext.getContext().get(PENDING_ACTIONS_KEY);
        if (collector instanceof List<?> raw) {
            @SuppressWarnings("unchecked")
            List<PendingActionResponse> list = (List<PendingActionResponse>) raw;
            // model gọi tool 2 lần cho cùng 1 sách -> store trả lại cùng action, tránh hiện 2 nút
            if (list.stream().noneMatch(r -> r.actionId().equals(response.actionId()))) {
                list.add(response);
            }
        }
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private BookSummary toSummary(BookResponse b) {
        return new BookSummary(
                b.getId(), b.getTitle(), b.getAuthor(),
                b.getCategory() != null ? b.getCategory().getName() : null,
                b.getAvailableCopies(),
                b.getStatus() != null ? b.getStatus().name() : null);
    }

    private BookDetail toDetail(BookResponse b) {
        return new BookDetail(
                b.getId(), b.getTitle(), b.getAuthor(), b.getPublisher(), b.getPublishYear(),
                b.getLanguage(), b.getDescription(),
                b.getCategory() != null ? b.getCategory().getName() : null,
                b.getTotalCopies(), b.getAvailableCopies(),
                b.getStatus() != null ? b.getStatus().name() : null,
                b.getShelfLocation(), b.getPrice(),
                b.getMyReservationStatus() != null ? b.getMyReservationStatus().name() : null,
                b.getMyReservationQueuePosition());
    }

    private FineInfo toFineInfo(FineResponse f) {
        return new FineInfo(
                f.getId(), f.getBookTitle(), f.getAmount(),
                f.getReason() != null ? f.getReason().name() : null,
                f.getStatus() != null ? f.getStatus().name() : null,
                f.getIssuedDate());
    }
}