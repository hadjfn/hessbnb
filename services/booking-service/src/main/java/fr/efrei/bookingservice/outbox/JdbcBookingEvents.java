package fr.efrei.bookingservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.efrei.bookingservice.dto.BookingResponse;
import fr.efrei.bookingservice.port.BookingEvents;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class JdbcBookingEvents implements BookingEvents {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public JdbcBookingEvents(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, BookingResponse booking, String cancelledBy) {
        var payload = json.valueToTree(booking);
        if (cancelledBy != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("cancelledBy", cancelledBy);
        }
        try {
            jdbc.update("""
                    INSERT INTO booking_outbox(event_id, aggregate_id, event_type, payload)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), booking.id(), eventType, json.writeValueAsString(payload));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not serialize booking event", error);
        }
    }
}
