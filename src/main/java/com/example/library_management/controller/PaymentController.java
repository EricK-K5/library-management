package com.example.library_management.controller;

import com.example.library_management.configuration.VnpConfig;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.PaymentResponse;
import com.example.library_management.service.PaymentService;
import com.example.library_management.service.PaymentService.CallbackResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/vnpay")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;
    VnpConfig vnpConfig;

    // MEMBER bam "Thanh toan online" -> nhan paymentUrl -> FE redirect sang VNPay
    @PostMapping("/fines/{fineId}")
    @PreAuthorize("hasAuthority('fine:pay')")
    public ApiResponse<PaymentResponse> createPayment(
            @PathVariable Long fineId,
            HttpServletRequest request) {
        return ApiResponse.<PaymentResponse>builder()
                .result(paymentService.createVnpayPayment(fineId, request))
                .build();
    }

    // PUBLIC (SecurityConfig): VNPay redirect TRINH DUYET cua user ve day, khong co JWT.
    // Xac thuc bang chu ky, xu ly xong redirect sang trang ket qua cua FE.
    @GetMapping("/return")
    public ResponseEntity<Void> handleReturn(HttpServletRequest request) {
        CallbackResult result = paymentService.processCallback(extractVnpParams(request));

        String status;
        if (result.code() == PaymentService.Code.INVALID_SIGNATURE
                || result.code() == PaymentService.Code.NOT_FOUND
                || result.code() == PaymentService.Code.INVALID_AMOUNT) {
            status = "invalid";
        } else {
            status = result.paid() ? "success" : "failed";
        }

        URI target = UriComponentsBuilder.fromUriString(vnpConfig.frontendResultUrl())
                .queryParam("status", status)
                .queryParamIfPresent("fineId", java.util.Optional.ofNullable(result.fineId()))
                .build()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }

    // PUBLIC: VNPay goi server-to-server (cau hinh URL trong portal merchant sandbox).
    // Local can ngrok moi nhan duoc; neu khong co thi Return URL o tren van cap nhat duoc.
    @GetMapping("/ipn")
    public Map<String, String> handleIpn(HttpServletRequest request) {
        CallbackResult result;
        try {
            result = paymentService.processCallback(extractVnpParams(request));
        } catch (Exception exception) {
            return ipn("99", "Unknown error");
        }

        return switch (result.code()) {
            case INVALID_SIGNATURE -> ipn("97", "Invalid signature");
            case NOT_FOUND -> ipn("01", "Order not found");
            case INVALID_AMOUNT -> ipn("04", "Invalid amount");
            case ALREADY_CONFIRMED -> ipn("02", "Order already confirmed");
            case PROCESSED -> ipn("00", "Confirm Success");
        };
    }

    private static Map<String, String> ipn(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }

    private static Map<String, String> extractVnpParams(HttpServletRequest request) {
        Map<String, String> params = new TreeMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (key.startsWith("vnp_") && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
