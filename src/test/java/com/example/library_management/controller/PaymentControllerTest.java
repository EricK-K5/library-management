package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.VnpConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.response.PaymentResponse;
import com.example.library_management.enums.Code;
import com.example.library_management.enums.PaymentStatus;
import com.example.library_management.service.PaymentService;
import com.example.library_management.service.PaymentService.CallbackResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /vnpay/return va /vnpay/ipn la PUBLIC (VNPay goi, khong co JWT) -> test khong gui token.
@WebMvcTest(
        controllers = PaymentController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebMvcConfig.class))
@Import({SecurityConfig.class, PaymentControllerTest.TestConfig.class})
class PaymentControllerTest {

    // VnpConfig la @ConfigurationProperties record, slice khong tu nap -> cung cap ban that voi gia tri co dinh
    @TestConfiguration
    static class TestConfig {
        @Bean
        VnpConfig vnpConfig() {
            return new VnpConfig(true, "https://sandbox.example/pay", "TMN123", "test-secret",
                    "http://localhost:8080/api/vnpay/return", "http://localhost:5173/fines/payment-result",
                    "2.1.0", "pay", "other");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentService paymentService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static final String FRONTEND = "http://localhost:5173/fines/payment-result";

    // POST /vnpay/fines/{fineId}

    @Test
    void createPayment_noToken_returns401() throws Exception {
        mockMvc.perform(post("/vnpay/fines/3"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void createPayment_withoutFinePayAuthority_returns403() throws Exception {
        mockMvc.perform(post("/vnpay/fines/3").with(jwt().authorities(new SimpleGrantedAuthority("fine:read"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    @Test
    void createPayment_withAuthority_returnsPaymentUrlAndTxnRef() throws Exception {
        when(paymentService.createVnpayPayment(eq(3L), any(HttpServletRequest.class)))
                .thenReturn(new PaymentResponse("https://sandbox.example/pay?x=1", "REF1"));

        mockMvc.perform(post("/vnpay/fines/3").with(jwt().authorities(new SimpleGrantedAuthority("fine:pay"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.paymentUrl").value("https://sandbox.example/pay?x=1"))
                .andExpect(jsonPath("$.result.txnRef").value("REF1"));
    }

    //GET /vnpay/return (trinh duyet user duoc VNPay redirect ve)

    @Test
    void handleReturn_paid_redirectsToFrontendWithSuccessAndOnlyForwardsVnpParams() throws Exception {
        when(paymentService.processCallback(any()))
                .thenReturn(new CallbackResult(Code.PROCESSED, "REF1", 3L, PaymentStatus.SUCCESS));

        mockMvc.perform(get("/vnpay/return")
                        .param("vnp_TxnRef", "REF1")
                        .param("vnp_ResponseCode", "00")
                        .param("other", "ignored"))                    // khong phai tham so vnp_ -> khong duoc chuyen vao service
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, FRONTEND + "?status=success&fineId=3"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(paymentService).processCallback(captor.capture());
        assertEquals(Map.of("vnp_TxnRef", "REF1", "vnp_ResponseCode", "00"), Map.copyOf(captor.getValue()));
    }

    @Test
    void handleReturn_paymentFailed_redirectsWithFailedStatus() throws Exception {
        when(paymentService.processCallback(any()))
                .thenReturn(new CallbackResult(Code.PROCESSED, "REF1", 3L, PaymentStatus.FAILED));

        mockMvc.perform(get("/vnpay/return").param("vnp_TxnRef", "REF1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, FRONTEND + "?status=failed&fineId=3"));
    }

    @Test
    void handleReturn_invalidSignature_redirectsWithInvalidStatusAndNoFineId() throws Exception {
        when(paymentService.processCallback(any()))
                .thenReturn(new CallbackResult(Code.INVALID_SIGNATURE, "REF1", null, null));

        mockMvc.perform(get("/vnpay/return").param("vnp_TxnRef", "REF1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, FRONTEND + "?status=invalid"));
    }

    //GET /vnpay/ipn (VNPay goi server-to-server)

    static Stream<Arguments> ipnCases() {
        return Stream.of(
                Arguments.of(Code.PROCESSED, "00", "Confirm Success"),
                Arguments.of(Code.INVALID_SIGNATURE, "97", "Invalid signature"),
                Arguments.of(Code.NOT_FOUND, "01", "Order not found"),
                Arguments.of(Code.INVALID_AMOUNT, "04", "Invalid amount"),
                Arguments.of(Code.ALREADY_CONFIRMED, "02", "Order already confirmed")
        );
    }

    @ParameterizedTest
    @MethodSource("ipnCases")
    void handleIpn_mapsEachResultToVnpayRspCode(Code code, String rspCode, String message) throws Exception {
        when(paymentService.processCallback(any()))
                .thenReturn(new CallbackResult(code, "REF1", 3L, PaymentStatus.SUCCESS));

        mockMvc.perform(get("/vnpay/ipn").param("vnp_TxnRef", "REF1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value(rspCode))
                .andExpect(jsonPath("$.Message").value(message));
    }

//    IPN SERVER-TO-SERVER
    @Test
    void handleIpn_serviceThrows_returnsRspCode99() throws Exception {
        when(paymentService.processCallback(any())).thenThrow(new IllegalStateException("db down"));

        mockMvc.perform(get("/vnpay/ipn").param("vnp_TxnRef", "REF1"))
                .andExpect(status().isOk())                        // IPN luon tra 200 + RspCode de VNPay biet ma xu ly
                .andExpect(jsonPath("$.RspCode").value("99"));
    }
}