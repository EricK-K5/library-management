package com.example.library_management.dto.request;

import com.example.library_management.enums.FineReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class FineCreationRequest {

    @NotNull(message = "BorrowRecordId khong duoc de trong")
    Long borrowRecordId;

    @NotNull(message = "So tien khong duoc de trong")
    @DecimalMin(value = "0", inclusive = false, message = "So tien phai > 0")
    BigDecimal amount;

    @NotNull(message = "Ly do khong duoc de trong")
    FineReason reason;

    @Size(max = 255)
    String note;
}
