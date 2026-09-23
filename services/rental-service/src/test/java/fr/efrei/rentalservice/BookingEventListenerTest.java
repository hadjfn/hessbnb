package fr.efrei.rentalservice;

import fr.efrei.rentalservice.listener.BookingEventListener;
import fr.efrei.rentalservice.service.RentalService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingEventListenerTest {
    @Test void doesNotAcknowledgePersistenceFailure() {
        var service = mock(RentalService.class);
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var listener = new BookingEventListener(service);
        assertThrows(DataAccessResourceFailureException.class, () -> listener.handleBookingConfirmed(validEvent()));
    }
    @Test void rejectsMalformedEventWithoutInfiniteRetry() {
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> new BookingEventListener(mock(RentalService.class)).handleBookingConfirmed(Map.of("id", "invalid")));
    }
    private Map<String, Object> validEvent() {
        return Map.of("id", UUID.randomUUID().toString(), "listingId", UUID.randomUUID().toString(),
                "tenantId", UUID.randomUUID().toString(), "ownerId", UUID.randomUUID().toString(),
                "checkInDate", "2027-03-01", "checkOutDate", "2027-03-04", "totalPrice", "20.00");
    }
}
