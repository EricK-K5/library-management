package com.example.library_management.mapper;

import com.example.library_management.dto.response.BorrowRecordResponse;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.util.OverduePolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {UserMapper.class, BookMapper.class, FineMapper.class}
)
public interface BorrowRecordMapper {

    @Mapping(target = "processedByUsername", source = "processedBy.username")
    @Mapping(target = "issuedByUsername", source = "issuedBy.username")
    @Mapping(target = "receivedByUsername", source = "receivedBy.username")
    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "overdueDays", ignore = true)
    BorrowRecordResponse toBorrowRecordResponseBase(BorrowRecord borrowRecord);

    default BorrowRecordResponse toBorrowRecordResponse(BorrowRecord borrowRecord) {
        BorrowRecordResponse response =
                toBorrowRecordResponseBase(borrowRecord);

        if (response != null && borrowRecord != null) {
            response.setOverdueDays(
                    OverduePolicy.overdueDays(borrowRecord)
            );
        }

        return response;
    }
}