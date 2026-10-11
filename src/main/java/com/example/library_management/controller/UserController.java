package com.example.library_management.controller;

import com.example.library_management.dto.request.UserCreationRequest;
import com.example.library_management.dto.request.UserUpdateRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;

    // CREATE USER
    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }

    // GET USER
    @PreAuthorize("hasAuthority('user:read')")
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable String id) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUser(id))
                .build();
    }

    // GET ALL USER
    @PreAuthorize("hasAuthority('user:read')")
    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUser() {
        return ApiResponse.<List<UserResponse>>builder()
                .result(userService.getAllUser())
                .build();
    }

    // UPDATE USER
    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String id,
            @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(id, request))
                .build();
    }

    // DELETE USER
    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> updateMyInfo(@RequestBody @Valid UserUpdateRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateMyInfo(request))
                .build();
    }

    // ===== AVATAR: multipart/form-data, field "file" =====

    // DOI AVATAR CUA CHINH MINH
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateMyAvatar(file))
                .build();
    }

    // XOA AVATAR CUA CHINH MINH
    @DeleteMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> deleteMyAvatar() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.deleteMyAvatar())
                .build();
    }

    // ADMIN DOI AVATAR CHO USER KHAC
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> uploadAvatar(
            @PathVariable String id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateAvatar(id, file))
                .build();
    }

    // ADMIN XOA AVATAR CUA USER KHAC
    @DeleteMapping("/{id}/avatar")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserResponse> deleteAvatar(@PathVariable String id) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.deleteAvatar(id))
                .build();
    }
}
