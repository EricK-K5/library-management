package com.example.library_management.dto.request;

import com.example.library_management.validator.DobContraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = 8, message = "PASSWORD_INVALID")
    String password;

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
