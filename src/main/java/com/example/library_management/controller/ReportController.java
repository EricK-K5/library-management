package com.example.library_management.controller;

import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.FineReportResponse;
import com.example.library_management.dto.response.TopBooksReportResponse;
import com.example.library_management.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    ReportService reportService;

    // ?startDate=2026-10-01&endDate=2026-10-31&status=ALL|UNPAID|PAID|WAIVED&fineType=ALL|OVERDUE|LOST_BOOK|DAMAGED_BOOK&page=0&size=20
    @GetMapping("/fines")
    @PreAuthorize("hasAuthority('report:read')")
    public ApiResponse<FineReportResponse> getFineReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "ALL") String fineType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.<FineReportResponse>builder()
                .result(reportService.getFineReport(startDate, endDate, status, fineType, pageable))
                .build();
    }

    @GetMapping("/fines/export")
    @PreAuthorize("hasAuthority('report:export')")
    public ResponseEntity<byte[]> exportFineReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "ALL") String fineType) {
        byte[] file = reportService.exportFineReport(startDate, endDate, status, fineType);
        return excel(file, "fine-report-" + LocalDate.now() + ".xlsx");
    }

    // ?startDate=...&endDate=...&limit=5|10|20|ALL&categoryId=1
    @GetMapping("/top-books")
    @PreAuthorize("hasAuthority('report:read')")
    public ApiResponse<TopBooksReportResponse> getTopBooksReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "10") String limit,
            @RequestParam(required = false) String categoryId) {
        return ApiResponse.<TopBooksReportResponse>builder()
                .result(reportService.getTopBooksReport(startDate, endDate, limit, categoryId))
                .build();
    }

    @GetMapping("/top-books/export")
    @PreAuthorize("hasAuthority('report:export')")
    public ResponseEntity<byte[]> exportTopBooksReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "10") String limit,
            @RequestParam(required = false) String categoryId) {
        byte[] file = reportService.exportTopBooksReport(startDate, endDate, limit, categoryId);
        return excel(file, "most-borrowed-books-" + LocalDate.now() + ".xlsx");
    }

    private ResponseEntity<byte[]> excel(byte[] file, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentLength(file.length)
                .body(file);
    }
}
