package com.example.library_management.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequest {

    @NotBlank(message = "Code khong duoc de trong")
    String code; // vd: "book:read"

    String description;

    @NotBlank(message = "Module khong duoc de trong")
    String module; // vd: "BOOK"
}
