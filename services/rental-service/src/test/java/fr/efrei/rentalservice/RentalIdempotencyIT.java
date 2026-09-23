package fr.efrei.rentalservice;

import fr.efrei.rentalservice.dto.RentalCreateRequest;
import fr.efrei.rentalservice.service.RentalService;
import fr.efrei.rentalservice.repository.RentalRepository;
import fr.efrei.rentalservice.entity.RentalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"})
class RentalIdempotencyIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=schema_rentals");
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired RentalService service;
    @Autowired RentalRepository repository;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder decoder;
    @BeforeEach void clean() { jdbc.execute("TRUNCATE schema_rentals.rentals"); }

    @Test void repeatedEventKeepsCompletedRentalState() {
        var request = request();
        var first = service.create(request);
        var rental = repository.findById(first.id()).orElseThrow();
        rental.setStatus(RentalStatus.COMPLETED);
        repository.saveAndFlush(rental);
        var duplicate = service.create(request);
        assertEquals(first.id(), duplicate.id());
        assertEquals("COMPLETED", duplicate.status());
        assertEquals(1, repository.count());
    }
    @Test void concurrentDeliveriesReturnSameRental() throws Exception {
        var request = request();
        var start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UUID> create = () -> { start.await(10, TimeUnit.SECONDS); return service.create(request).id(); };
            var a = pool.submit(create); var b = pool.submit(create);
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, repository.count());
    }
    private RentalCreateRequest request() {
        return new RentalCreateRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 4), null, new BigDecimal("100.00"));
    }
}
