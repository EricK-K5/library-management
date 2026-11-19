package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.UserCreationRequest;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = WebMvcConfig.class))
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static String body(String password) {
        return """
                {"username": "khoa", "password": "%s", "fullName": "Tri Khoa",
                 "email": "Khoa@example.com", "phoneNumber": "0912345678"}
                """.formatted(password);
    }

//    CREATE USER
    @Test
    void createUser_noToken_validRequest() throws Exception {
        when(userService.createUser(any(UserCreationRequest.class))).thenReturn(
                UserResponse.builder().id("u1").username("khoa").roles(Set.of("MEMBER")).build());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("12345678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.username").value("khoa"))
                .andExpect(jsonPath("$.result.password").doesNotExist());
    }

    @Test
    void createUser_shortPassword() throws Exception {
        // @Size(min = 8, message = "INVALID_PASSWORD") -> GlobalException map sang ErrorCode 1004
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));

        verifyNoInteractions(userService);
    }

    @Test
    void createUser_usernameExisted() throws Exception {
        when(userService.createUser(any(UserCreationRequest.class)))
                .thenThrow(new AppException(ErrorCode.USERS_EXISTED));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123456788")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("User existed"));
    }
}