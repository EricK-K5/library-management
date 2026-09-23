package com.example.library_management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiveFineRequest {

    @NotBlank(message = "Ly do mien phat khong duoc de trong")
    @Size(max = 200)
    String reason;
}
