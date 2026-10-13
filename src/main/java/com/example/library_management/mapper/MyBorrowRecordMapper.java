package com.example.library_management.mapper;

import com.example.library_management.dto.response.MyBorrowRecordResponse;
import com.example.library_management.entity.BorrowRecord;
import com.example.library_management.mapper.BookMapper;
import com.example.library_management.mapper.FineMapper;
import com.example.library_management.mapper.UserMapper;
import com.example.library_management.util.OverduePolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {UserMapper.class, BookMapper.class, FineMapper.class}
)
public interface MyBorrowRecordMapper {

    @Mapping(target = "processedByUsername", source = "processedBy.username")
    @Mapping(target = "issuedByUsername", source = "issuedBy.username")
    @Mapping(target = "receivedByUsername", source = "receivedBy.username")
    @Mapping(target = "reservationId", source = "reservation.id")
    @Mapping(target = "overdue", ignore = true)
    @Mapping(target = "overdueDays", ignore = true)
    MyBorrowRecordResponse toMyBorrowRecordResponseBase(
            BorrowRecord borrowRecord
    );

    default MyBorrowRecordResponse toMyBorrowRecordResponse(
            BorrowRecord borrowRecord
    ) {
        MyBorrowRecordResponse response =
                toMyBorrowRecordResponseBase(borrowRecord);

        if (response != null && borrowRecord != null) {
            long days = OverduePolicy.overdueDays(borrowRecord);

            response.setOverdue(days > 0);
            response.setOverdueDays(days);
        }

        return response;
    }
}