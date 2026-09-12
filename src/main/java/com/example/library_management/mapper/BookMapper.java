package com.example.library_management.mapper;

import com.example.library_management.dto.request.BookRequest;
import com.example.library_management.dto.response.BookResponse;
import com.example.library_management.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        uses = CategoryMapper.class,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface BookMapper {

//    KHONG MAP CATEGORY
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "availableCopies", source = "totalCopies")
    Book toBook(BookRequest request);

    BookResponse toBookResponse(Book book);

    @Mapping(target = "category", ignore = true)
    @Mapping(target = "availableCopies", ignore = true)
    void updateBook(@MappingTarget Book book, BookRequest request);
}