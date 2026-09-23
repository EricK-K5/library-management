package com.example.library_management.controller;

import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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

    // MEMBER tu huy dat sach cua chinh minh (chi khi con PENDING)
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

    // LIBRARIAN/ADMIN duyet thu cong (bi chan neu khong phai dau hang doi)
//    @PutMapping("/{id}/accept")
//    @PreAuthorize("hasAuthority('reservation:manage')")
//    public ApiResponse<ReservationResponse> acceptReservation(@PathVariable Long id) {
//        return ApiResponse.<ReservationResponse>builder()
//                .result(reservationService.acceptReservationManually(id))
//                .build();
//    }

    // Danh sach dat sach cua chinh minh (MEMBER)
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('reservation:read')")
    public ApiResponse<List<ReservationResponse>> getMyReservations() {
        return ApiResponse.<List<ReservationResponse>>builder()
                .result(reservationService.getMyReservations())
                .build();
    }

    // Danh sach tat ca (LIBRARIAN/ADMIN)
    @GetMapping
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<List<ReservationResponse>> getAllReservations() {
        return ApiResponse.<List<ReservationResponse>>builder()
                .result(reservationService.getAllReservations())
                .build();
    }

    // Danh sach dat sach cua 1 quyen sach cu the, xep theo hang doi (LIBRARIAN/ADMIN)
    @GetMapping("/book/{bookId}")
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<List<ReservationResponse>> getReservationsByBook(@PathVariable Long bookId) {
        return ApiResponse.<List<ReservationResponse>>builder()
                .result(reservationService.getReservationsByBook(bookId))
                .build();
    }
}
