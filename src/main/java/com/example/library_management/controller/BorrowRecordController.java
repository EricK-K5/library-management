package com.example.library_management.controller;

import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.request.HandoverRequest;
import com.example.library_management.dto.request.MarkLostRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.service.BorrowRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/borrow-records")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class BorrowRecordController {

    BorrowRecordService borrowRecordService;

    // LIBRARIAN/ADMIN xac nhan giao sach cho 1 reservation da duoc giu cho (ACCEPTED) -> tao BorrowRecord.
    // Member KHONG tu tao BorrowRecord duoc: chi dat sach qua POST /reservations.
    @PostMapping("/handover")
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<BorrowRecordResponse> handoverBook(@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.<BorrowRecordResponse>builder()
                .result(borrowRecordService.handoverBook(request))
                .build();
    }

    // Chi LIBRARIAN/ADMIN duoc xac nhan tra sach
    @PutMapping("/{id}/return")
    @PreAuthorize("hasAuthority('borrow:return')")
    public ApiResponse<BorrowRecordResponse> returnBook(
            @PathVariable Long id, @RequestBody(required = false) BorrowReturnRequest request) {
        return ApiResponse.<BorrowRecordResponse>builder()
                .result(borrowRecordService.returnBook(id, request))
                .build();
    }

    // Danh sach cua chinh minh (MEMBER)
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('borrow:read')")
    public ApiResponse<List<MyBorrowRecordResponse>> getMyBorrowRecords() {
        return ApiResponse.<List<MyBorrowRecordResponse>>builder()
                .result(borrowRecordService.getMyBorrowRecords())
                .build();
    }

    // Danh sach tat ca (LIBRARIAN/ADMIN), phan trang: ?page=0&size=20, moi nhat truoc
    @GetMapping
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<Page<BorrowRecordResponse>> getAllBorrowRecords(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<Page<BorrowRecordResponse>>builder()
                .result(borrowRecordService.getAllBorrowRecords(pageable))
                .build();
    }

    // Danh sach borrow dang qua han (LIBRARIAN/ADMIN), phan trang, tre lau nhat len dau
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<Page<BorrowRecordResponse>> getOverdueRecords(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.<Page<BorrowRecordResponse>>builder()
                .result(borrowRecordService.getOverdueRecords(pageable))
                .build();
    }

    // Chi tiet 1 borrow record (LIBRARIAN/ADMIN)
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<BorrowRecordResponse> getBorrowRecordById(@PathVariable Long id) {
        return ApiResponse.<BorrowRecordResponse>builder()
                .result(borrowRecordService.getBorrowRecordById(id))
                .build();
    }

    // Danh sach muon cua 1 user cu the (LIBRARIAN/ADMIN)
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<List<BorrowRecordResponse>> getBorrowRecordsByUser(@PathVariable String userId) {
        return ApiResponse.<List<BorrowRecordResponse>>builder()
                .result(borrowRecordService.getBorrowRecordsByUser(userId))
                .build();
    }

    // Bao mat sach: chi LIBRARIAN/ADMIN. Tao Fine (tre han + den sach), giam totalCopies, record van chiem slot
    // trong han muc muon cho toi khi het Fine UNPAID. LOST la trang thai cuoi, khong tra lai duoc.
    @PutMapping("/{id}/lost")
    @PreAuthorize("hasAuthority('borrow:return')")
    public ApiResponse<BorrowRecordResponse> markLost(
            @PathVariable Long id, @Valid @RequestBody(required = false) MarkLostRequest request) {
        return ApiResponse.<BorrowRecordResponse>builder()
                .result(borrowRecordService.markLost(id, request))
                .build();
    }
}
