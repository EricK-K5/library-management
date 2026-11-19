package com.example.library_management.controller;

import com.example.library_management.configuration.CustomJwtDecoder;
import com.example.library_management.configuration.SecurityConfig;
import com.example.library_management.configuration.WebMvcConfig;
import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.enums.BookStatus;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.BookService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BookController.class,
        properties = "app.cors.allowed-origins=http://localhost:5173",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = WebMvcConfig.class)
)
@Import(SecurityConfig.class)
public class BookControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookService bookService;

    @MockitoBean
    CustomJwtDecoder customJwtDecoder;

    private static final String VALID_BODY = """
            {
              "title": "Clean Code",
              "isbn": "978-0132350884",
              "author": "Robert C. Martin",
              "totalCopies": 3,
              "categoryId": 1
            }
            """;

    private static BookResponse sampleResponse() {
        return BookResponse.builder().id(1L).title("Clean Code").isbn("978-0132350884")
                .author("Robert C. Martin").totalCopies(3).availableCopies(3)
                .status(BookStatus.AVAILABLE).build();
    }

    //    GET
    @Test
    void getAllBooks_noToken() throws Exception {
        when(bookService.getAllBooks(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(sampleResponse()),PageRequest.of(0,10),1)
        );

        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect((jsonPath("$.code")).value(1000))
                .andExpect(jsonPath("$.result.content[0].title").value("Clean Code"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookService).getAllBooks(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(Sort.by("title"), captor.getValue().getSort());
    }

//    GET BOOK BY ID

    @Test
    void getBookById_notFound() throws Exception {
        when(bookService.getBookById(99L)).thenThrow(new AppException(ErrorCode.BOOK_NOT_EXISTED));

        mockMvc.perform(get("/books/99")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("Book not existed"));
    }

//    POST BOOK BUT NO TOKEN
    @Test
    void createBook_noToken() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        verifyNoInteractions(bookService);
    }

//    DONT HAVE PERMISSION
    @Test
    void createBook_withoutBookCreateAuthority() throws Exception {
        mockMvc.perform(post("/books")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        verifyNoInteractions(bookService);
    }

//    BLANK ISBN
    @Test
    void createBook_blankIsbn() throws Exception {
        mockMvc.perform(post("/books")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {"title": "Clean Code", "isbn": "  ", "author": "Robert C. Martin",
                                     "totalCopies": 3, "categoryId": 1}
                                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3008));

        verifyNoInteractions(bookService);
    }

//    CREATE VALID
    @Test
    void createBook_validRequest() throws Exception {
        when(bookService.createBook(any(BookRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/books")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.id").value(1))
                .andExpect(jsonPath("$.result.status").value("AVAILABLE"));
    }

//    DELETE BOOK
    @Test
    void deleteBook_withAuthority_returns200AndCallsService() throws Exception {
        mockMvc.perform(delete("/books/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("book:delete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000));

        verify(bookService).deleteBook(1L);
    }
}