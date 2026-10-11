package com.example.library_management.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Thu thu xac nhan giao sach cho 1 reservation dang ACCEPTED (da giu cho)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverRequest {

    @NotNull(message = "ReservationId khong duoc de trong")
    Long reservationId;
}
