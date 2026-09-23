package fr.efrei.bookingservice;

import fr.efrei.bookingservice.domain.BookingStatus;
import fr.efrei.bookingservice.outbox.OutboxPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.AmqpConnectException;
import static org.mockito.Mockito.*;

import fr.efrei.bookingservice.dto.BookingCreateRequest;
import fr.efrei.bookingservice.service.BookingService;
import fr.efrei.bookingservice.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@Testcontainers
@SpringBootTest(properties = {"booking.outbox.enabled=false", "spring.rabbitmq.dynamic=false"})
@AutoConfigureMockMvc
class BookingReliabilityIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=schema_bookings");
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired BookingService service;
    @Autowired BookingRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean RabbitTemplate rabbit;
    UUID owner = UUID.randomUUID(), tenant = UUID.randomUUID(), listing = UUID.randomUUID();
    LocalDate arrival = LocalDate.now().plusDays(10);
    @BeforeEach void clean() { jdbc.execute("TRUNCATE schema_bookings.bookings, schema_bookings.booking_outbox"); }

    @Test void createsBookingAndDurableEventTogether() {
        var result = service.create(tenant, request(arrival, arrival.plusDays(2)));
        assertEquals("PENDING", result.status());
        assertEquals(1, count("bookings"));
        assertEquals(1, count("booking_outbox"));
        assertEquals(result.id(), jdbc.queryForObject("SELECT aggregate_id FROM booking_outbox", UUID.class));
    }
    @Test void outerRollbackLeavesNoBookingOrEvent() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.create(tenant, request(arrival, arrival.plusDays(2)));
            throw new IllegalStateException("simulate later transaction failure");
        }));
        assertEquals(0, count("bookings"));
        assertEquals(0, count("booking_outbox"));
    }
    @Test void permitsAdjacentStaysAndFreesCancelledDates() {
        var first = service.create(tenant, request(arrival, arrival.plusDays(2)));
        service.create(tenant, request(arrival.plusDays(2), arrival.plusDays(4)));
        assertThrows(RuntimeException.class, () -> service.create(tenant, request(arrival, arrival.plusDays(1))));
        service.cancel(first.id(), tenant);
        assertDoesNotThrow(() -> service.create(tenant, request(arrival, arrival.plusDays(1))));
    }
    @Test void databaseRejectsOverlapEvenWhenApplicationPrecheckIsBypassed() {
        service.create(tenant, request(arrival, arrival.plusDays(2)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO bookings(id, listing_id, tenant_id, owner_id, check_in_date, check_out_date, guests_count, total_price)
                VALUES (?, ?, ?, ?, ?, ?, 1, 10)
                """, UUID.randomUUID(), listing, tenant, owner, arrival, arrival.plusDays(1)));
        assertEquals(1, count("bookings"));
    }
    @Test void concurrentRequestsCannotDoubleBook() throws Exception {
        var start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> reserve = () -> {
                start.await(10, TimeUnit.SECONDS);
                try { service.create(tenant, request(arrival, arrival.plusDays(2))); return true; }
                catch (org.springframework.dao.DataIntegrityViolationException | IllegalStateException expected) { return false; }
            };
            var a = pool.submit(reserve); var b = pool.submit(reserve);
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, count("bookings"));
        assertEquals(1, count("booking_outbox"));
    }
    @Test void optimisticVersionPreventsLostStatusUpdates() {
        var result = service.create(tenant, request(arrival, arrival.plusDays(2)));
        var first = repository.findById(result.id()).orElseThrow();
        var stale = repository.findById(result.id()).orElseThrow();
        first.setStatus(BookingStatus.CONFIRMED);
        repository.saveAndFlush(first);
        stale.setStatus(BookingStatus.CANCELLED);
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
        assertEquals(BookingStatus.CONFIRMED, repository.findById(result.id()).orElseThrow().getStatus());
    }
    @Test void protectsReadAndBulkCancellationAtHttpBoundary() throws Exception {
        var booking = service.create(tenant, request(arrival, arrival.plusDays(2)));
        mvc.perform(patch("/api/bookings/listing/{id}/cancel-all", listing)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings/{id}", booking.id()).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/bookings/listing/{id}/cancel-all", listing).with(jwt().jwt(j -> j.subject(tenant.toString()))))
                .andExpect(status().isForbidden());
        assertEquals("PENDING", service.getById(booking.id(), owner).status());
        mvc.perform(patch("/api/bookings/listing/{id}/cancel-all", listing).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
        assertEquals("CANCELLED", service.getById(booking.id(), tenant).status());
        assertEquals(2, count("booking_outbox"));
    }
    @Test void outboxPreservesPerBookingOrderAndDeletesOnlyAfterBrokerAcknowledgement() {
        var booking = service.create(tenant, request(arrival, arrival.plusDays(2)));
        service.confirm(booking.id(), owner);
        var sent = new java.util.ArrayList<String>();
        doAnswer(call -> {
            sent.add(call.getArgument(1));
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publish();
        assertEquals(java.util.List.of("booking.created"), sent);
        assertEquals(1, count("booking_outbox"));
        publish();
        assertEquals(java.util.List.of("booking.created", "booking.confirmed"), sent);
        assertEquals(0, count("booking_outbox"));
    }
    @Test void brokerFailureLeavesDurableEventForRetry() {
        service.create(tenant, request(arrival, arrival.plusDays(2)));
        doThrow(new AmqpConnectException(new java.net.ConnectException("offline"))).when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publish();
        assertEquals(1, count("booking_outbox"));
        assertEquals(1, count("bookings"));
    }
    @Test void negativeBrokerAcknowledgementRetainsEvent() {
        service.create(tenant, request(arrival, arrival.plusDays(2)));
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker refused"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publish();
        assertEquals(1, count("booking_outbox"));
    }
    @Test void unroutableMessageIsNotMistakenForSuccessfulDelivery() {
        service.create(tenant, request(arrival, arrival.plusDays(2)));
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "hessbnb.exchange", "booking.created"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publish();
        assertEquals(1, count("booking_outbox"));
    }
    private void publish() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> new OutboxPublisher(jdbc, rabbit).publishPending());
    }
    private BookingCreateRequest request(LocalDate from, LocalDate to) {
        return new BookingCreateRequest(listing, owner, from, to, 2, new BigDecimal("30.00"), null);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
}
