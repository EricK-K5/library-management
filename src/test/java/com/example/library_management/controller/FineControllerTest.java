package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.FineCreationRequest;
import com.example.library_management.dto.request.WaiveFineRequest;
import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.enums.FineReason;
import com.example.library_management.enums.FineStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.FineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Quyen: member fine:read | thu thu fine:manage (xem, thu tien) | fine:create (phat thu cong) | fine:waive (chi ADMIN mien phat)
@WebMvcTest(
        controllers = FineController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = WebMvcConfig.class))
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class FineControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FineService fineService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static RequestPostProcessor authority(String code) {
        return jwt().authorities(new SimpleGrantedAuthority(code));
    }

    private static FineResponse sample() {
        return FineResponse.builder().id(3L).amount(new BigDecimal("15000"))
                .reason(FineReason.OVERDUE).status(FineStatus.UNPAID).build();
    }

    // GET /fines/my

    @Test
    void getMy_noToken_returns401() throws Exception {
        mockMvc.perform(get("/fines/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fineService);
    }

    @Test
    void getMy_withReadAuthority_returnsList() throws Exception {
        when(fineService.getMyFines()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/fines/my").with(authority("fine:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result[0].id").value(3))
                .andExpect(jsonPath("$.result[0].status").value("UNPAID"));
    }

    // GET /fines (thu thu)

    @Test
    void getFines_memberAuthority_returns403() throws Exception {
        mockMvc.perform(get("/fines").with(authority("fine:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(fineService);
    }

    @Test
    void getFines_withFilters_passesThemAndAppliesDefaultPaging() throws Exception {
        when(fineService.getFines(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/fines")
                        .with(authority("fine:manage"))
                        .param("keyword", "nguyen")
                        .param("status", "UNPAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(3));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(fineService).getFines(eq("nguyen"), eq(FineStatus.UNPAID), captor.capture());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), captor.getValue().getSort()); // moi nhat truoc
    }

    @Test
    void getFines_noFilters_passesNulls() throws Exception {
        when(fineService.getFines(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/fines").with(authority("fine:manage")))
                .andExpect(status().isOk());

        verify(fineService).getFines(isNull(), isNull(), any(Pageable.class));
    }

    // GET /fines/{id}

    @Test
    void getFineById_found_returns200() throws Exception {
        when(fineService.getFineById(3L)).thenReturn(sample());

        mockMvc.perform(get("/fines/3").with(authority("fine:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(3));
    }

    @Test
    void getFineById_notFound_returns404() throws Exception {
        when(fineService.getFineById(99L)).thenThrow(new AppException(ErrorCode.FINE_NOT_EXISTED));

        mockMvc.perform(get("/fines/99").with(authority("fine:manage")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6005));
    }

    // GET /fines/unpaid-total

    @Test
    void getTotalUnpaid_withManageAuthority_returnsAmount() throws Exception {
        when(fineService.getTotalUnpaidAmount()).thenReturn(new BigDecimal("125000"));

        mockMvc.perform(get("/fines/unpaid-total").with(authority("fine:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(125000));
    }

    @Test
    void getTotalUnpaid_memberAuthority_returns403() throws Exception {
        mockMvc.perform(get("/fines/unpaid-total").with(authority("fine:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fineService);
    }

    // PUT /fines/{id}/pay

    @Test
    void pay_memberAuthority_returns403() throws Exception {
        mockMvc.perform(put("/fines/3/pay").with(authority("fine:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fineService);
    }

    @Test
    void pay_withManageAuthority_returnsPaidFine() throws Exception {
        when(fineService.payFine(3L)).thenReturn(
                FineResponse.builder().id(3L).status(FineStatus.PAID).build());

        mockMvc.perform(put("/fines/3/pay").with(authority("fine:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PAID"));
    }

    @Test
    void pay_alreadySettled_returns400WithErrorCode() throws Exception {
        when(fineService.payFine(3L)).thenThrow(new AppException(ErrorCode.FINE_NOT_UNPAID));

        mockMvc.perform(put("/fines/3/pay").with(authority("fine:manage")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6000));
    }

    // PUT /fines/{id}/waive (chi ADMIN)

    @Test
    void waive_librarianWithManageOnly_returns403() throws Exception {
        // Thu thu thu tien duoc nhung KHONG duoc mien phat: can quyen rieng fine:waive
        mockMvc.perform(put("/fines/3/waive")
                        .with(authority("fine:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Hoan canh kho khan\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(fineService);
    }

    @Test
    void waive_blankReason_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(put("/fines/3/waive")
                        .with(authority("fine:waive"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));

        verifyNoInteractions(fineService);
    }

    @Test
    void waive_withAuthorityAndReason_returnsWaivedFineAndPassesReason() throws Exception {
        when(fineService.waiveFine(eq(3L), any(WaiveFineRequest.class)))
                .thenReturn(FineResponse.builder().id(3L).status(FineStatus.WAIVED).build());

        mockMvc.perform(put("/fines/3/waive")
                        .with(authority("fine:waive"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Hoan canh kho khan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("WAIVED"));

        ArgumentCaptor<WaiveFineRequest> captor = ArgumentCaptor.forClass(WaiveFineRequest.class);
        verify(fineService).waiveFine(eq(3L), captor.capture());
        assertEquals("Hoan canh kho khan", captor.getValue().getReason());
    }

    // POST /fines (phat thu cong)

    private static final String DAMAGED_BODY = """
            {"borrowRecordId": 1, "amount": 30000, "reason": "DAMAGED_BOOK", "note": "Rach bia"}
            """;

    @Test
    void createFine_noToken_returns401() throws Exception {
        mockMvc.perform(post("/fines").contentType(MediaType.APPLICATION_JSON).content(DAMAGED_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fineService);
    }

    @Test
    void createFine_manageOnly_returns403() throws Exception {
        // Tao phat thu cong can fine:create rieng
        mockMvc.perform(post("/fines")
                        .with(authority("fine:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAMAGED_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fineService);
    }

    @Test
    void createFine_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"borrowRecordId\": 1, \"amount\": 0, \"reason\": \"DAMAGED_BOOK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));

        verifyNoInteractions(fineService);
    }

    @Test
    void createFine_missingBorrowRecordId_returns400() throws Exception {
        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30000, \"reason\": \"DAMAGED_BOOK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));

        verifyNoInteractions(fineService);
    }

    @Test
    void createFine_valid_returns200AndPassesRequestFields() throws Exception {
        when(fineService.createManualFine(any(FineCreationRequest.class)))
                .thenReturn(FineResponse.builder().id(7L).reason(FineReason.DAMAGED_BOOK).status(FineStatus.UNPAID).build());

        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAMAGED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(7))
                .andExpect(jsonPath("$.result.reason").value("DAMAGED_BOOK"));

        ArgumentCaptor<FineCreationRequest> captor = ArgumentCaptor.forClass(FineCreationRequest.class);
        verify(fineService).createManualFine(captor.capture());
        assertEquals(1L, captor.getValue().getBorrowRecordId());
        assertEquals(0, new BigDecimal("30000").compareTo(captor.getValue().getAmount()));
        assertEquals(FineReason.DAMAGED_BOOK, captor.getValue().getReason());
        assertEquals("Rach bia", captor.getValue().getNote());
    }

    @Test
    void createFine_reasonNotAllowed_returns400WithErrorCode() throws Exception {
        when(fineService.createManualFine(any(FineCreationRequest.class)))
                .thenThrow(new AppException(ErrorCode.FINE_REASON_NOT_ALLOWED));

        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAMAGED_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6003));
    }

    @Test
    void createFine_alreadyExisted_returns400WithErrorCode() throws Exception {
        when(fineService.createManualFine(any(FineCreationRequest.class)))
                .thenThrow(new AppException(ErrorCode.FINE_ALREADY_EXISTED));

        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAMAGED_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6004));
    }

    @Test
    void createFine_borrowRecordNotFound_returns404() throws Exception {
        when(fineService.createManualFine(any(FineCreationRequest.class)))
                .thenThrow(new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

        mockMvc.perform(post("/fines")
                        .with(authority("fine:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DAMAGED_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }
}