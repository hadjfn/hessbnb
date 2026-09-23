package fr.efrei.bookingservice;

import fr.efrei.bookingservice.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BookingPolicyTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2027, 3, 1);

    @Test void acceptsPositiveStayStartingToday() {
        assertDoesNotThrow(() -> validate(today, today.plusDays(1), 1, new BigDecimal("10.50")));
    }
    @Test void rejectsZeroNightsAndReversedDates() {
        assertThrows(IllegalArgumentException.class, () -> validate(today, today, 1, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> validate(today, today.minusDays(1), 1, BigDecimal.TEN));
    }
    @Test void rejectsPastStay() {
        assertThrows(IllegalArgumentException.class, () -> validate(today.minusDays(1), today, 1, BigDecimal.TEN));
    }
    @Test void rejectsNullOrZeroGuestCount() {
        assertThrows(IllegalArgumentException.class, () -> validate(today, today.plusDays(1), null, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> validate(today, today.plusDays(1), 0, BigDecimal.TEN));
    }
    @Test void rejectsInvalidMoneyInsteadOfRoundingIt() {
        for (BigDecimal price : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-10"), new BigDecimal("1.001"), new BigDecimal("100000000")}) {
            assertThrows(IllegalArgumentException.class, () -> validate(today, today.plusDays(1), 1, price));
        }
    }
    @Test void preventsBookingOwnListing() {
        assertThrows(IllegalArgumentException.class, () -> BookingPolicy.validateStay(owner, owner, today,
                today.plusDays(1), 1, BigDecimal.TEN, today));
    }
    @Test void onlyParticipantsMayAccessBooking() {
        assertDoesNotThrow(() -> BookingPolicy.requireParticipant(tenant, owner, tenant));
        assertDoesNotThrow(() -> BookingPolicy.requireParticipant(tenant, owner, owner));
        assertThrows(BookingAccessDeniedException.class,
                () -> BookingPolicy.requireParticipant(tenant, owner, UUID.randomUUID()));
    }
    @Test void onlyOwnerMayConfirm() {
        assertThrows(BookingAccessDeniedException.class, () -> BookingPolicy.requireOwner(owner, tenant));
        assertEquals(BookingStatus.CONFIRMED, BookingPolicy.confirm(BookingStatus.PENDING));
    }
    @ParameterizedTest @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "CANCELLED", "COMPLETED"})
    void rejectsInvalidConfirmation(BookingStatus status) {
        assertThrows(IllegalStateException.class, () -> BookingPolicy.confirm(status));
    }
    @ParameterizedTest @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "COMPLETED"})
    void rejectsCancellationOfTerminalBooking(BookingStatus status) {
        assertThrows(IllegalStateException.class, () -> BookingPolicy.cancel(status));
    }
    private void validate(LocalDate start, LocalDate end, Integer guests, BigDecimal price) {
        BookingPolicy.validateStay(tenant, owner, start, end, guests, price, today);
    }
}
