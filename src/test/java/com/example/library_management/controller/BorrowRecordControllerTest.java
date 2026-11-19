package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.BorrowReturnRequest;
import com.example.library_management.dto.request.HandoverRequest;
import com.example.library_management.dto.request.MarkLostRequest;
import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.enums.BorrowStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.BorrowRecordService;
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

@WebMvcTest(
        controllers = BorrowRecordController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = WebMvcConfig.class))
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class BorrowRecordControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BorrowRecordService borrowRecordService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

//    HELPER
    private static RequestPostProcessor authority(String code) {
        return jwt().authorities(new SimpleGrantedAuthority(code));
    }

    private static BorrowRecordResponse sample() {
        return BorrowRecordResponse.builder().id(100L).build();
    }

    //POST /borrow-records/handover

    @Test
    void handover_noToken() throws Exception {
        mockMvc.perform(post("/borrow-records/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": 1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void handover_memberAuthority() throws Exception {
        mockMvc.perform(post("/borrow-records/handover")
                        .with(authority("borrow:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void handover_missingReservationId() throws Exception {
        // message "ReservationId khong duoc de trong" khong phai ten enum -> rơi ve INVALID_KEY (code 1)
        mockMvc.perform(post("/borrow-records/handover")
                        .with(authority("borrow:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void handover_success() throws Exception {
        when(borrowRecordService.handoverBook(any(HandoverRequest.class))).thenReturn(sample());

        mockMvc.perform(post("/borrow-records/handover")
                        .with(authority("borrow:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.id").value(100));

        ArgumentCaptor<HandoverRequest> captor = ArgumentCaptor.forClass(HandoverRequest.class);
        verify(borrowRecordService).handoverBook(captor.capture());
        assertEquals(7L, captor.getValue().getReservationId());
    }

    @Test
    void handover_reservationNotAccepted() throws Exception {
        when(borrowRecordService.handoverBook(any(HandoverRequest.class)))
                .thenThrow(new AppException(ErrorCode.RESERVATION_NOT_ACCEPTED));

        mockMvc.perform(post("/borrow-records/handover")
                        .with(authority("borrow:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": 7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5006))
                .andExpect(jsonPath("$.message").value("Reservation not accepted"));
    }

    //PUT /borrow-records/{id}/return

    @Test
    void returnBook_manageOnlyAuthority() throws Exception {
        // Endpoint nay can borrow:return
        mockMvc.perform(put("/borrow-records/1/return")
                        .with(authority("borrow:manage")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void returnBook_withoutBody() throws Exception {
        when(borrowRecordService.returnBook(eq(1L), isNull())).thenReturn(sample());
        mockMvc.perform(put("/borrow-records/1/return").with(authority("borrow:return")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(100));

        verify(borrowRecordService).returnBook(eq(1L), isNull());
    }

    @Test
    void returnBook_withNote() throws Exception {
        when(borrowRecordService.returnBook(eq(1L), any(BorrowReturnRequest.class))).thenReturn(sample());

        mockMvc.perform(put("/borrow-records/1/return")
                        .with(authority("borrow:return"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Sach con tot\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BorrowReturnRequest> captor = ArgumentCaptor.forClass(BorrowReturnRequest.class);
        verify(borrowRecordService).returnBook(eq(1L), captor.capture());
        assertEquals("Sach con tot", captor.getValue().getNote());
    }

    @Test
    void returnBook_recordNotFound() throws Exception {
        when(borrowRecordService.returnBook(eq(99L), isNull()))
                .thenThrow(new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

        mockMvc.perform(put("/borrow-records/99/return").with(authority("borrow:return")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void returnBook_alreadyReturned() throws Exception {
        when(borrowRecordService.returnBook(eq(1L), isNull()))
                .thenThrow(new AppException(ErrorCode.BORROW_ALREADY_RETURNED));

        mockMvc.perform(put("/borrow-records/1/return").with(authority("borrow:return")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4003));
    }

    //PUT /borrow-records/{id}/lost

    @Test
    void markLost_noToken() throws Exception {
        mockMvc.perform(put("/borrow-records/1/lost"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void markLost_withoutBody() throws Exception {
        when(borrowRecordService.markLost(eq(1L), isNull())).thenReturn(sample());

        mockMvc.perform(put("/borrow-records/1/lost").with(authority("borrow:return")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void markLost_withAmountAndNote() throws Exception {
        when(borrowRecordService.markLost(eq(1L), any(MarkLostRequest.class))).thenReturn(sample());

        mockMvc.perform(put("/borrow-records/1/lost")
                        .with(authority("borrow:return"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Lam mat\", \"lostAmount\": 150000}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MarkLostRequest> captor = ArgumentCaptor.forClass(MarkLostRequest.class);
        verify(borrowRecordService).markLost(eq(1L), captor.capture());
        assertEquals("Lam mat", captor.getValue().getNote());
        assertEquals(0, new BigDecimal("150000").compareTo(captor.getValue().getLostAmount()));
    }

    @Test
    void markLost_zeroAmount() throws Exception {
        // @DecimalMin(value = "0", inclusive = false): so tien phai > 0
        mockMvc.perform(put("/borrow-records/1/lost")
                        .with(authority("borrow:return"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lostAmount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1));

        verifyNoInteractions(borrowRecordService);
    }

    //GET /borrow-records (danh sach, thu thu)

    @Test
    void getAll_memberAuthority() throws Exception {
        mockMvc.perform(get("/borrow-records").with(authority("borrow:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void getAll_withFilters() throws Exception {
        when(borrowRecordService.getAllBorrowRecords(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/borrow-records")
                        .with(authority("borrow:manage"))
                        .param("status", "BORROWED")
                        .param("keyword", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(100));

        // @PageableDefault(size = 20, sort = "id", direction = DESC): moi nhat truoc
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(borrowRecordService).getAllBorrowRecords(eq(BorrowStatus.BORROWED), eq("john"), captor.capture());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), captor.getValue().getSort());
    }

    @Test
    void getAll_noFilters() throws Exception {
        when(borrowRecordService.getAllBorrowRecords(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/borrow-records").with(authority("borrow:manage")))
                .andExpect(status().isOk());

        verify(borrowRecordService).getAllBorrowRecords(isNull(), isNull(), any(Pageable.class));
    }

    //GET /borrow-records/overdue

    @Test
    void getOverdue_withKeyword() throws Exception {
        when(borrowRecordService.getOverdueRecords(eq("clean"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/borrow-records/overdue")
                        .with(authority("borrow:manage"))
                        .param("keyword", "clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(100));
    }

    //GET /borrow-records/{id} va /user/{userId}

    @Test
    void getById_found() throws Exception {
        when(borrowRecordService.getBorrowRecordById(1L)).thenReturn(sample());

        mockMvc.perform(get("/borrow-records/1").with(authority("borrow:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(100));
    }

    @Test
    void getById_notFound() throws Exception {
        when(borrowRecordService.getBorrowRecordById(99L))
                .thenThrow(new AppException(ErrorCode.BORROW_RECORD_NOT_EXISTED));

        mockMvc.perform(get("/borrow-records/99").with(authority("borrow:manage")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void getByUser_returnsListForThatUser() throws Exception {
        when(borrowRecordService.getBorrowRecordsByUser("m1")).thenReturn(List.of(sample()));

        mockMvc.perform(get("/borrow-records/user/m1").with(authority("borrow:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(100));
    }

    //GET /borrow-records/my

    @Test
    void getMy_noToken() throws Exception {
        mockMvc.perform(get("/borrow-records/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(borrowRecordService);
    }

    @Test
    void getMy_memberAuthority_returns200() throws Exception {
        when(borrowRecordService.getMyBorrowRecords())
                .thenReturn(List.of(MyBorrowRecordResponse.builder().id(1L).build()));

        mockMvc.perform(get("/borrow-records/my").with(authority("borrow:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(1));
    }
}