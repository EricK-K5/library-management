package com.example.library_management.controller;

import com.example.library_management.dto.request.FineCreationRequest;
import com.example.library_management.dto.request.WaiveFineRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.service.FineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class FineController {

    FineService fineService;

    // MEMBER xem phi phat cua chinh minh
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('fine:read')")
    public ApiResponse<List<FineResponse>> getMyFines() {
        return ApiResponse.<List<FineResponse>>builder()
                .result(fineService.getMyFines())
                .build();
    }

    // LIBRARIAN/ADMIN tra cuu
    @GetMapping
    @PreAuthorize("hasAuthority('fine:manage')")
    public ApiResponse<List<FineResponse>> getFines(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) FineStatus status) {
        return ApiResponse.<List<FineResponse>>builder()
                .result(fineService.getFines(userId, status))
                .build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fine:manage')")
    public ApiResponse<FineResponse> getFineById(@PathVariable Long id) {
        return ApiResponse.<FineResponse>builder()
                .result(fineService.getFineById(id))
                .build();
    }
    // Thu tien tai quay: UNPAID -> PAID
    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('fine:manage')")
    public ApiResponse<FineResponse> payFine(@PathVariable Long id) {
        return ApiResponse.<FineResponse>builder()
                .result(fineService.payFine(id))
                .build();
    }

    // Mien phat - chi ADMIN
    @PutMapping("/{id}/waive")
    @PreAuthorize("hasAuthority('fine:waive')")
    public ApiResponse<FineResponse> waiveFine(
            @PathVariable Long id, @Valid @RequestBody WaiveFineRequest request) {
        return ApiResponse.<FineResponse>builder()
                .result(fineService.waiveFine(id, request))
                .build();
    }

    // Phat thu cong(Ap dung cho sach bi hu/rach trong luc tra)
    @PostMapping
    @PreAuthorize("hasAuthority('fine:create')")
    public ApiResponse<FineResponse> createFine(@Valid @RequestBody FineCreationRequest request) {
        return ApiResponse.<FineResponse>builder()
                .result(fineService.createManualFine(request))
                .build();
    }
}
