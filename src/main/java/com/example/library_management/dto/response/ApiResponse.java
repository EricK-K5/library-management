package com.example.library_management.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class ApiResponse<T> {
    @Builder.Default
    int code = 1000;

    @Builder.Default
    String message = "SUCCESS";

    T result;
}
