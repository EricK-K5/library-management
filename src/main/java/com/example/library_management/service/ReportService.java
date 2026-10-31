package com.example.library_management.service;

import com.example.library_management.dto.request.FineReportFilter;
import com.example.library_management.dto.response.*;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.CategoryRepository;
import com.example.library_management.repository.FineRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bao cao cho thu thu/admin: Fine Report va Most Borrowed Books.
 *
 * Fine Report: loc theo Fine.issuedDate (ngay ghi nhan phat), gom 2 dau. Phat "tam tinh" cua sach chua tra
 * (OVERDUE + UNPAID + luot muon dang BORROWED/OVERDUE) bi loai.
 *
 * Most Borrowed Books: 1 BorrowRecord = 1 luot muon (record chi duoc tao luc giao sach), loc theo
 * BorrowRecord.borrowDate, dem ca RETURNED / BORROWED / OVERDUE / LOST.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ReportService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_TOP_LIMIT = 10;

    FineRepository fineRepository;
    BorrowRecordRepository borrowRecordRepository;
    CategoryRepository categoryRepository;
    ReportExcelService reportExcelService;

    // ================= FINE REPORT =================

    @Transactional(readOnly = true)
    public FineReportResponse getFineReport(String startDate, String endDate, String status, String fineType,
                                            Pageable pageable) {
        FineReportFilter filter = parseFineFilter(startDate, endDate, status, fineType);
        return buildFineReport(filter, sanitize(pageable));
    }

    @Transactional(readOnly = true)
    public byte[] exportFineReport(String startDate, String endDate, String status, String fineType) {
        FineReportFilter filter = parseFineFilter(startDate, endDate, status, fineType);
        // Export lay toan bo dong khop bo loc (khong phan trang)
        return reportExcelService.buildFineReport(buildFineReport(filter, Pageable.unpaged()));
    }

    private FineReportResponse buildFineReport(FineReportFilter f, Pageable pageable) {
        List<FineStatusTotal> totals = fineRepository.sumReportTotals(
                f.startDate(), f.endDate(), f.status(), f.fineType());
        Page<FineReportRow> rows = fineRepository.findReportRows(
                f.startDate(), f.endDate(), f.status(), f.fineType(), pageable);

        return FineReportResponse.builder()
                .generatedAt(LocalDateTime.now())
                .startDate(f.startDate())
                .endDate(f.endDate())
                .status(f.status())
                .fineType(f.fineType())
                .summary(buildFineSummary(totals))
                .details(rows)
                .build();
    }

    // Moi fine chi o 1 trong 3 trang thai nen total = paid + unpaid + waived, khong dem trung
    FineReportSummary buildFineSummary(List<FineStatusTotal> totals) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        BigDecimal waived = BigDecimal.ZERO;
        long count = 0;

        for (FineStatusTotal t : totals) {
            BigDecimal amount = t.amount() == null ? BigDecimal.ZERO : t.amount();
            switch (t.status()) {
                case PAID -> paid = paid.add(amount);
                case UNPAID -> unpaid = unpaid.add(amount);
                case WAIVED -> waived = waived.add(amount);
            }
            count += t.count() == null ? 0 : t.count();
        }

        return FineReportSummary.builder()
                .totalRecordedAmount(paid.add(unpaid).add(waived))
                .totalPaidAmount(paid)
                .totalUnpaidAmount(unpaid)
                .totalWaivedAmount(waived)
                .totalFineRecords(count)
                .build();
    }

    FineReportFilter parseFineFilter(String startDate, String endDate, String status, String fineType) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        validateDateRange(start, end);
        FineStatus fineStatus = parseEnum(status, FineStatus.class);
        FineReason reason = parseEnum(fineType, FineReason.class);
        return new FineReportFilter(start, end, fineStatus, reason);
    }

    // ================= MOST BORROWED BOOKS =================

    @Transactional(readOnly = true)
    public TopBooksReportResponse getTopBooksReport(String startDate, String endDate, String limit,
                                                    String categoryId) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        validateDateRange(start, end);
        Integer top = parseLimit(limit);
        Long category = parseCategoryId(categoryId);

        String categoryName = null;
        if (category != null) {
            categoryName = categoryRepository.findById(category)
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_EXISTED))
                    .getName();
        }

        // Summary tinh tren toan bo ket qua da loc, khong bi cat boi limit
        BorrowTotals totals = borrowRecordRepository.findBorrowTotals(start, end, category);
        List<BookBorrowCount> ranked = borrowRecordRepository.findMostBorrowedBooks(
                start, end, category, top == null ? Pageable.unpaged() : PageRequest.of(0, top));

        List<BookRankingRow> rankings = new ArrayList<>(ranked.size());
        int rank = 1;
        for (BookBorrowCount b : ranked) {
            rankings.add(BookRankingRow.builder()
                    .rank(rank++)
                    .bookId(b.bookId())
                    .bookTitle(b.title())
                    .category(b.categoryName())
                    .borrowCount(b.borrowCount())
                    .build());
        }

        // Dong dau cua danh sach da sap xep chinh la sach muon nhieu nhat (cung quy tac sap xep)
        BookBorrowCount most = ranked.isEmpty() ? null : ranked.get(0);
        TopBooksSummary summary = TopBooksSummary.builder()
                .totalBorrowTransactions(totals == null || totals.totalTransactions() == null ? 0 : totals.totalTransactions())
                .distinctBooksBorrowed(totals == null || totals.distinctBooks() == null ? 0 : totals.distinctBooks())
                .mostBorrowedBookId(most == null ? null : most.bookId())
                .mostBorrowedBookTitle(most == null ? null : most.title())
                .mostBorrowedBorrowCount(most == null ? null : most.borrowCount())
                .build();

        return TopBooksReportResponse.builder()
                .generatedAt(LocalDateTime.now())
                .startDate(start)
                .endDate(end)
                .categoryId(category)
                .categoryName(categoryName)
                .limit(top)
                .summary(summary)
                .rankings(rankings)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportTopBooksReport(String startDate, String endDate, String limit, String categoryId) {
        return reportExcelService.buildTopBooksReport(getTopBooksReport(startDate, endDate, limit, categoryId));
    }

    // ================= VALIDATION HELPERS =================

    private Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        // Bo sort do client gui len, thu tu bao cao co dinh (deterministic)
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()); // yyyy-MM-dd
        } catch (DateTimeParseException e) {
            throw new AppException(ErrorCode.INVALID_REPORT_FILTER);
        }
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new AppException(ErrorCode.INVALID_REPORT_DATE_RANGE);
        }
    }

    // null / blank / ALL -> null (khong loc)
    private <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REPORT_FILTER);
        }
    }

    // Cho phep 5, 10, 20 hoac ALL. Mac dinh 10. Tra ve null neu ALL.
    private Integer parseLimit(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_TOP_LIMIT;
        }
        String v = value.trim();
        if ("ALL".equalsIgnoreCase(v)) {
            return null;
        }
        return switch (v) {
            case "5" -> 5;
            case "10" -> 10;
            case "20" -> 20;
            default -> throw new AppException(ErrorCode.INVALID_REPORT_FILTER);
        };
    }

    private Long parseCategoryId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.INVALID_REPORT_FILTER);
        }
    }
}
