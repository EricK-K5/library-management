package com.example.library_management.controller;

import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.service.ReservationService;
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
@RequestMapping("/reservations")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ReservationController {

    ReservationService reservationService;

    // MEMBER dat sach online
    @PostMapping
    @PreAuthorize("hasAuthority('reservation:create')")
    public ApiResponse<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        return ApiResponse.<ReservationResponse>builder()
                .result(reservationService.createReservation(request))
                .build();
    }

    // MEMBER tu huy dat sach cua chinh minh (PENDING hoac ACCEPTED)
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('reservation:cancel')")
    public ApiResponse<ReservationResponse> cancelMyReservation(@PathVariable Long id) {
        return ApiResponse.<ReservationResponse>builder()
                .result(reservationService.cancelMyReservation(id))
                .build();
    }

    // LIBRARIAN/ADMIN huy ho (PENDING hoac ACCEPTED)
    @PutMapping("/{id}/staff-cancel")
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<ReservationResponse> cancelReservationByStaff(@PathVariable Long id) {
        return ApiResponse.<ReservationResponse>builder()
                .result(reservationService.cancelReservationByStaff(id))
                .build();
    }

    // Danh sach dat sach cua chinh minh (MEMBER)
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('reservation:read')")
    public ApiResponse<List<ReservationResponse>> getMyReservations() {
        return ApiResponse.<List<ReservationResponse>>builder()
                .result(reservationService.getMyReservations())
                .build();
    }

    // Danh sach tat ca (LIBRARIAN/ADMIN), phan trang: ?page=0&size=20, moi nhat truoc
    @GetMapping
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<Page<ReservationResponse>> getAllReservations(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<Page<ReservationResponse>>builder()
                .result(reservationService.getAllReservations(pageable))
                .build();
    }

    // Danh sach dat sach cua 1 quyen sach cu the, phan trang, xep theo thoi gian dat (LIBRARIAN/ADMIN)
    @GetMapping("/book/{bookId}")
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<Page<ReservationResponse>> getReservationsByBook(
            @PathVariable Long bookId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.<Page<ReservationResponse>>builder()
                .result(reservationService.getReservationsByBook(bookId, pageable))
                .build();
    }
}
