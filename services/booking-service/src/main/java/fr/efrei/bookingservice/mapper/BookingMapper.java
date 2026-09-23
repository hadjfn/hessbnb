package fr.efrei.bookingservice.mapper;

import fr.efrei.bookingservice.dto.BookingCreateRequest;
import fr.efrei.bookingservice.dto.BookingResponse;
import fr.efrei.bookingservice.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "status", expression = "java(booking.getStatus().name())")
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Booking toEntity(BookingCreateRequest request);
}
