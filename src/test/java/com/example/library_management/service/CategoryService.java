package com.example.library_management.service;

import com.example.library_management.dto.request.CategoryRequest;
import com.example.library_management.dto.response.CategoryResponse;
import com.example.library_management.entity.Book;
import com.example.library_management.entity.Category;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.CategoryMapper;
import com.example.library_management.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    CategoryRepository categoryRepository;

    // Dung mapper that (MapStruct) de test luon ca viec map request -> entity -> response
    CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper);
    }

    // CRETA

    @Test
    void createCategory_success_returnsSavedCategory() {
        CategoryRequest request = CategoryRequest.builder().name("Science").description("Sach khoa hoc").build();
        when(categoryRepository.existsByName("Science")).thenReturn(false);
        // Gia lap DB gan id cho ban ghi sau khi save
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals(1L, response.getId());
        assertEquals("Science", response.getName());
        assertEquals("Sach khoa hoc", response.getDescription());
    }

    @Test
    void createCategory_nameExisted_throwsAndDoesNotSave() {
        CategoryRequest request = CategoryRequest.builder().name("Science").build();
        when(categoryRepository.existsByName("Science")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> categoryService.createCategory(request));

        assertEquals(ErrorCode.CATEGORY_EXISTED, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    // GET BY ID

    @Test
    void getCategoryById_notFound_throwsCategoryNotExisted() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> categoryService.getCategoryById(99L));

        assertEquals(ErrorCode.CATEGORY_NOT_EXISTED, ex.getErrorCode());
    }

    // DELETE

    @Test
    void deleteCategory_noBooks_deletesCategory() {
        Category category = Category.builder().id(1L).name("Science").build(); // books mac dinh rong
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_hasBooks_throwsCategoryInUse() {
        Category category = Category.builder().id(1L).name("Science")
                .books(Set.of(new Book()))
                .build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class, () -> categoryService.deleteCategory(1L));

        assertEquals(ErrorCode.CATEGORY_IN_USE, ex.getErrorCode());
        verify(categoryRepository, never()).delete(any());
    }
}