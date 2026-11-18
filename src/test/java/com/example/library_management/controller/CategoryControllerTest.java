package com.example.library_management.controller;


import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.CategoryRequest;
import com.example.library_management.dto.response.CategoryResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

@WebMvcTest(
        controllers = CategoryController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes =  WebMvcConfig.class)
)
@Import(SecurityConfig.class)
public class CategoryControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static final String VALID_BODY = """
            {"name": "Science", "description": "Sach khoa hoc"}
            """;

//    GET ALL
    @Test
    void  getAllCategories() throws Exception{
        when(categoryService.getAllCategories()).thenReturn(List.of(
                CategoryResponse.builder().id(1L).name("Science").description("Sach khoa hoc").build()));

        mockMvc.perform(get("/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result[0].id").value(1))
                .andExpect(jsonPath("$.result[0].name").value("Science"));
    }

//    GET id
    @Test
    void  getCategoriesById_notFound() throws Exception{
        when(categoryService.getCategoryById(100L)).thenThrow(new AppException(ErrorCode.CATEGORY_NOT_EXISTED));

        mockMvc.perform(get("/categories/100")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.message").value("Category not existed"));
    }

//    POST BUT NO TOKEN
    @Test
    void createCategory_noToken() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

//        ensure la service khong duoc goi
        verifyNoInteractions(categoryService);
    }

//    CREATE BUT DONT HAVE PERMISSION
    @Test
    void createCategories_noPermission() throws Exception{
        mockMvc.perform(post("/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(categoryService);
    }

//    CREATE BLANK NAME
    @Test
    void createCategory_blankName() throws Exception {
        mockMvc.perform(post("/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {"name": "   ", "description": "x"}
                                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1)); // INVALID_KEY (0001)

        verifyNoInteractions(categoryService);
    }

    //    VALID
    @Test
    void createCategory_validRequest() throws Exception {
        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(
                CategoryResponse.builder().id(1L).name("Science").description("Sach khoa hoc").build());

        mockMvc.perform(post("/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.id").value(1))
                .andExpect(jsonPath("$.result.name").value("Science"));
    }
}
