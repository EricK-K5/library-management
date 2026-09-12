package com.example.library_management.mapper;

import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.entity.BorrowRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, BookMapper.class, FineMapper.class})
public interface BorrowRecordMapper {

    @Mapping(target = "processedByUsername", source = "processedBy.username")
    BorrowRecordResponse toBorrowRecordResponse(BorrowRecord borrowRecord);
}
