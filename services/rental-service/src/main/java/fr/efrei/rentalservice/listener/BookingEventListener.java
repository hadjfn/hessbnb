package fr.efrei.rentalservice.listener;

import fr.efrei.rentalservice.config.RabbitMQConfig;
import fr.efrei.rentalservice.dto.RentalCreateRequest;
import fr.efrei.rentalservice.service.RentalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final RentalService rentalService;

    @RabbitListener(queues = RabbitMQConfig.RENTAL_QUEUE)
    public void handleBookingConfirmed(Map<String, Object> event) {
        RentalCreateRequest request;
        try {
            request = new RentalCreateRequest(
                    UUID.fromString((String) event.get("id")),
                    UUID.fromString((String) event.get("listingId")),
                    UUID.fromString((String) event.get("tenantId")),
                    UUID.fromString((String) event.get("ownerId")),
                    LocalDate.parse((String) event.get("checkInDate")),
                    LocalDate.parse((String) event.get("checkOutDate")),
                    null,
                    new BigDecimal(event.get("totalPrice").toString())
            );
        } catch (RuntimeException error) {
            // Parsing failures are permanent; infrastructure failures below must propagate.
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                    "Invalid booking.confirmed payload", error);
        }
        rentalService.create(request);
        log.info("Rental confirmation processed for booking {}", request.bookingId());
    }
}
