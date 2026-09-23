package fr.efrei.bookingservice.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Rules independent from HTTP, persistence and messaging. */
public final class BookingPolicy {
    private BookingPolicy() {}

    public static void validateStay(UUID tenantId, UUID ownerId, LocalDate checkIn,
                                    LocalDate checkOut, Integer guests, BigDecimal price, LocalDate today) {
        if (tenantId == null || ownerId == null || checkIn == null || checkOut == null
                || guests == null || price == null) {
            throw new IllegalArgumentException("Booking fields must not be null");
        }
        if (checkIn.isBefore(today) || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("A stay must start today or later and end after check-in");
        }
        if (guests < 1 || price.signum() <= 0 || price.scale() > 2
                || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("Guests and price must be positive; price allows at most two decimal places");
        }
        if (tenantId.equals(ownerId)) {
            throw new IllegalArgumentException("You cannot book your own listing");
        }
    }

    public static void requireOwner(UUID ownerId, UUID actorId) {
        if (!ownerId.equals(actorId)) {
            throw new BookingAccessDeniedException("Only the listing owner can perform this action");
        }
    }

    public static void requireParticipant(UUID tenantId, UUID ownerId, UUID actorId) {
        if (!tenantId.equals(actorId) && !ownerId.equals(actorId)) {
            throw new BookingAccessDeniedException("Only a booking participant can perform this action");
        }
    }

    public static BookingStatus confirm(BookingStatus current) {
        if (current != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be confirmed");
        }
        return BookingStatus.CONFIRMED;
    }

    public static BookingStatus cancel(BookingStatus current) {
        if (current != BookingStatus.PENDING && current != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("This booking cannot be cancelled");
        }
        return BookingStatus.CANCELLED;
    }
}
