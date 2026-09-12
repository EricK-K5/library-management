package com.example.library_management.mapper;

import com.example.library_management.dto.response.FineResponse;
import com.example.library_management.entity.Fine;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FineMapper {
    FineResponse toFineResponse(Fine fine);
}
