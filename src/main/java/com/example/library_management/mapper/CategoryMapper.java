package com.example.library_management.mapper;

import com.example.library_management.dto.request.CategoryRequest;
import com.example.library_management.dto.response.CategoryResponse;
import com.example.library_management.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    Category toCategory(CategoryRequest request);

    CategoryResponse toCategoryResponse(Category category);

    void updateCategory(@MappingTarget Category category, CategoryRequest request);
}
