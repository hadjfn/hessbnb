package fr.efrei.bookingservice.port;

import fr.efrei.bookingservice.dto.BookingResponse;

/** The implementation must join the booking database transaction. */
public interface BookingEvents {
    void record(String eventType, BookingResponse booking, String cancelledBy);
}
