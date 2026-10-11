package com.example.library_management.mapper;

import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.util.OverduePolicy;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {UserMapper.class, BookMapper.class, FineMapper.class})
public interface MyBorrowRecordMapper {

    @Mapping(target = "processedByUsername", source = "processedBy.username")
    @Mapping(target = "issuedByUsername", source = "issuedBy.username")
    @Mapping(target = "receivedByUsername", source = "receivedBy.username")
    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "overdue", ignore = true)
    @Mapping(target = "overdueDays", ignore = true)
    @Mapping(target = "estimatedFine", ignore = true)
    MyBorrowRecordResponse toMyBorrowRecordResponse(BorrowRecord borrowRecord);

    @AfterMapping
    default void fillOverdueInfo(BorrowRecord borrowRecord, @MappingTarget MyBorrowRecordResponse response) {
        OverduePolicy.OverdueInfo info = OverduePolicy.of(borrowRecord);
        response.setOverdue(info.overdue());
        response.setOverdueDays(info.overdueDays());
        response.setEstimatedFine(info.estimatedFine());
    }
}
