package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.ReservationRequest;
import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.enums.ReservationStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.ReservationService;
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

//  member: reservation:create / cancel / read   |   thu thu: reservation:manage (staff-cancel, xem danh sach)
@WebMvcTest(
        controllers = ReservationController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebMvcConfig.class))
@Import(SecurityConfig.class)
@EnableSpringDataWebSupport
class ReservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReservationService reservationService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static RequestPostProcessor authority(String code) {
        return jwt().authorities(new SimpleGrantedAuthority(code));
    }

    private static ReservationResponse sample() {
        return ReservationResponse.builder().id(10L).status(ReservationStatus.PENDING).queuePosition(2).build();
    }

    // POST /reservations

    @Test
    void create_noToken() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        verifyNoInteractions(reservationService);
    }

    @Test
    void create_staffAuthorityOnly() throws Exception {
        // Thu thu (reservation:manage) khong co quyen tao dat sach online cua member
        mockMvc.perform(post("/reservations")
                        .with(authority("reservation:manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(reservationService);
    }

    @Test
    void create_missingBookId() throws Exception {
        mockMvc.perform(post("/reservations")
                        .with(authority("reservation:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1)); // message khong phai ten enum -> INVALID_KEY

        verifyNoInteractions(reservationService);
    }

    @Test
    void create_success() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class))).thenReturn(sample());

        mockMvc.perform(post("/reservations")
                        .with(authority("reservation:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.id").value(10))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.queuePosition").value(2));

        ArgumentCaptor<ReservationRequest> captor = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(reservationService).createReservation(captor.capture());
        assertEquals(5L, captor.getValue().getBookId());
    }

    @Test
    void create_duplicated() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new AppException(ErrorCode.RESERVATION_DUPLICATED));

        mockMvc.perform(post("/reservations")
                        .with(authority("reservation:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5003));
    }

    @Test
    void create_limitExceeded() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new AppException(ErrorCode.BORROW_LIMIT_EXCEEDED));

        mockMvc.perform(post("/reservations")
                        .with(authority("reservation:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4002));
    }

    // PUT /reservations/{id}/cancel (member tu huy)

    @Test
    void cancelMine_staffAuthorityOnly() throws Exception {
        mockMvc.perform(put("/reservations/10/cancel").with(authority("reservation:manage")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void cancelMine_success() throws Exception {
        when(reservationService.cancelMyReservation(10L))
                .thenReturn(ReservationResponse.builder().id(10L).status(ReservationStatus.CANCELLED).build());

        mockMvc.perform(put("/reservations/10/cancel").with(authority("reservation:cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    @Test
    void cancelMine_notOwner() throws Exception {
        when(reservationService.cancelMyReservation(10L)).thenThrow(new AppException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(put("/reservations/10/cancel").with(authority("reservation:cancel")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    void cancelMine_notFound() throws Exception {
        when(reservationService.cancelMyReservation(99L)).thenThrow(new AppException(ErrorCode.RESERVATION_NOT_EXISTED));

        mockMvc.perform(put("/reservations/99/cancel").with(authority("reservation:cancel")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(5001));
    }

    // PUT /reservations/{id}/staff-cancel

    @Test
    void staffCancel_memberAuthority() throws Exception {
        mockMvc.perform(put("/reservations/10/staff-cancel").with(authority("reservation:cancel")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void staffCancel_success() throws Exception {
        when(reservationService.cancelReservationByStaff(10L))
                .thenReturn(ReservationResponse.builder().id(10L).status(ReservationStatus.CANCELLED).build());

        mockMvc.perform(put("/reservations/10/staff-cancel").with(authority("reservation:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    @Test
    void staffCancel_alreadyProcessed() throws Exception {
        when(reservationService.cancelReservationByStaff(10L))
                .thenThrow(new AppException(ErrorCode.RESERVATION_ALREADY_PROCESSED));

        mockMvc.perform(put("/reservations/10/staff-cancel").with(authority("reservation:manage")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5002));
    }

    // GET /reservations/my

    @Test
    void getMy_noToken() throws Exception {
        mockMvc.perform(get("/reservations/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationService);
    }

    @Test
    void getMy_withReadAuthority_returnsList() throws Exception {
        when(reservationService.getMyReservations()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/reservations/my").with(authority("reservation:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(10))
                .andExpect(jsonPath("$.result[0].queuePosition").value(2));
    }

    // GET /reservations (thu thu)

    @Test
    void getAll_memberAuthority() throws Exception {
        mockMvc.perform(get("/reservations").with(authority("reservation:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }

    @Test
    void getAll_withFilters_passesThemAndAppliesDefaultPaging() throws Exception {
        when(reservationService.getAllReservations(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/reservations")
                        .with(authority("reservation:manage"))
                        .param("status", "ACCEPTED")
                        .param("keyword", "nguyen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(10));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).getAllReservations(eq(ReservationStatus.ACCEPTED), eq("nguyen"), captor.capture());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), captor.getValue().getSort()); // moi nhat truoc
    }

    @Test
    void getAll_noFilters_passesNulls() throws Exception {
        when(reservationService.getAllReservations(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/reservations").with(authority("reservation:manage")))
                .andExpect(status().isOk());

        verify(reservationService).getAllReservations(isNull(), isNull(), any(Pageable.class));
    }

    // GET /reservations/book/{bookId}

    @Test
    void getByBook_appliesQueueOrderPagingAndPassesBookId() throws Exception {
        when(reservationService.getReservationsByBook(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/reservations/book/5").with(authority("reservation:manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].id").value(10));

        // Xep theo thoi gian dat (createdAt, id) tang dan = dung thu tu hang doi
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).getReservationsByBook(eq(5L), captor.capture());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.ASC, "createdAt", "id"), captor.getValue().getSort());
    }

    @Test
    void getByBook_memberAuthority() throws Exception {
        mockMvc.perform(get("/reservations/book/5").with(authority("reservation:read")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reservationService);
    }
}