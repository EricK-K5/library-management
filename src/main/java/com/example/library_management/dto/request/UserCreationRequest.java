package com.example.library_management.dto.request;

import com.example.library_management.validator.DobContraint;
import jakarta.persistence.Column;
import jakarta.validation.Valid;
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

public class UserCreationRequest {
    @NotBlank(message = "USERNAME_INVALID")
    @Size(min = 4, message = "USER_INVALID")
    String username;

    @NotBlank(message = "INVALID_PASSWORD")
    @Size(min = 8, message = "INVALID_PASSWORD")
    String password;

    @NotBlank(message = "FULL_NAME_REQUIRED")
    @Size(max = 50)
    String fullName;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL")
    String email;

    @NotBlank(message = "PHONE_REQUIRED")
    @Pattern(regexp = "^$|^[0-9]{9,10}$", message = "INVALID_PHONE")
    String phoneNumber;

    String address;

    @DobContraint(min = 12, message = "INVALID_DOB")
    LocalDate dob;
}
