package com.example.library_management.controller;

import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class AdminController {
    AdminService adminService;
    @PostMapping("/librarians/{username}")
    public ApiResponse<UserResponse> addLibrarian(@PathVariable String username) {
        return ApiResponse.<UserResponse>builder()
                .result(adminService.addLibrarian(username))
                .build();
    }
    @DeleteMapping("/librarians/{username}")
    public ApiResponse<UserResponse> removeLibrarian(@PathVariable String username) {
        return ApiResponse.<UserResponse>builder()
                .result(adminService.removeLibrarian(username))
                .build();
    }
}
