package com.example.library_management.mapper;

import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.entity.Fine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FineMapper {
    @Mapping(target = "borrowRecordId", source = "borrowRecord.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "bookTitle", source = "borrowRecord.book.title")
    @Mapping(target = "processedByUsername", source = "processedBy.username")
    FineResponse toFineResponse(Fine fine);
}
