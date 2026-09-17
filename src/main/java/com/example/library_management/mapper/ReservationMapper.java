package com.example.library_management.mapper;

import com.example.library_management.dto.response.ReservationResponse;
import com.example.library_management.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, BookMapper.class})
public interface ReservationMapper {

    @Mapping(target = "processedByUsername", source = "processedBy.username")
    @Mapping(target = "queuePosition", ignore = true)
    ReservationResponse toReservationResponse(Reservation reservation);
}
