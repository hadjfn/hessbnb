package fr.efrei.bookingservice.outbox;

import fr.efrei.bookingservice.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "booking.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
    }

    /** Row locks and the predecessor check preserve each booking's order across workers. */
    @Scheduled(fixedDelayString = "${booking.outbox.delay-ms:2000}")
    @Transactional
    public void publishPending() {
        var pending = jdbc.query("""
                SELECT current.id, current.event_id, current.event_type, current.payload
                FROM booking_outbox current
                WHERE NOT EXISTS (SELECT 1 FROM booking_outbox older
                    WHERE older.aggregate_id = current.aggregate_id AND older.id < current.id)
                ORDER BY current.id LIMIT 20 FOR UPDATE OF current SKIP LOCKED
                """, (row, n) -> new PendingEvent(row.getLong("id"), row.getObject("event_id", UUID.class),
                row.getString("event_type"), row.getString("payload")));
        for (var event : pending) {
            try {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(event.eventId().toString());
                CorrelationData confirmation = new CorrelationData(event.eventId().toString());
                rabbit.send(RabbitMQConfig.EXCHANGE, "booking." + event.type(),
                        new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties), confirmation);
                var ack = confirmation.getFuture().get(5, TimeUnit.SECONDS);
                if (!ack.isAck() || confirmation.getReturned() != null) {
                    throw new IllegalStateException("Broker did not route and acknowledge the event");
                }
                jdbc.update("DELETE FROM booking_outbox WHERE id = ?", event.id());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                // Leave the durable row for the next poll; no booking payload or personal data in logs.
                log.warn("Booking event {} remains pending: {}", event.eventId(), error.getClass().getSimpleName());
                return;
            }
        }
    }

    private record PendingEvent(long id, UUID eventId, String type, String payload) {}
}
