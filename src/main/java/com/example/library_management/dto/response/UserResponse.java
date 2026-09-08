package com.example.library_management.dto.response;

import com.example.library_management.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;

    String username;

    String fullName;

    String email;

    String phoneNumber;

    String address;

    String avatarUrl;

    UserStatus userStatus = UserStatus.ACTIVE;

    Set<String> roles;

    Integer maxBorrowLimit = 5;

    LocalDateTime createAt;

    LocalDateTime updatedAt;
}
