package com.example.library_management.controller;

import com.example.library_management.dto.request.BorrowRecordRequest;
import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.request.MarkLostRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.service.BorrowRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/borrow-records")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class BorrowRecordController {

    BorrowRecordService borrowRecordService;

    // MEMBER tu muon sach cho chinh minh
    @PostMapping
    @PreAuthorize("hasAuthority('borrow:create')")
    public ApiResponse<BorrowRecordResponse> borrowBook(@Valid @RequestBody BorrowRecordRequest request) {
        return ApiResponse.<BorrowRecordResponse>builder()
                .result(borrowRecordService.borrowBook(request))
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

    // Danh sach tat ca (LIBRARIAN/ADMIN)
    @GetMapping
    @PreAuthorize("hasAuthority('borrow:manage')")
    public ApiResponse<List<BorrowRecordResponse>> getAllBorrowRecords() {
        return ApiResponse.<List<BorrowRecordResponse>>builder()
                .result(borrowRecordService.getAllBorrowRecords())
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
