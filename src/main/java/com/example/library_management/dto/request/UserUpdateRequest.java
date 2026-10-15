package com.example.library_management.dto.request;

import com.example.library_management.validator.DobContraint;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)

public class UserUpdateRequest {
    @Size(min = 8, message = "INVALID_PASSWORD")
    String password;

    @NotBlank(message = "FULL_NAME_REQUIRED")
    @Size(max = 50)
    String fullName;

    @Email(message = "INVALID_EMAIL")
    String email;

    @Pattern(regexp = "^$|^[0-9]{9,10}$", message = "INVALID_PHONE")
    String phoneNumber;

    String address;

    @DobContraint(min = 12, message = "INVALID_DOB")
    LocalDate dob;
}
