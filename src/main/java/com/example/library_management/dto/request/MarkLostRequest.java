package com.example.library_management.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkLostRequest {

    @Size(max = 255)
    String note;

    // Khong bat buoc. Neu co: thay cho (gia sach + phi xu ly) - dung cho sach cu / het ban in / dinh gia rieng
    @DecimalMin(value = "0", inclusive = false, message = "So tien phai > 0")
    BigDecimal lostAmount;
}
