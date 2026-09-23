package fr.efrei.bookingservice.repository;

import fr.efrei.bookingservice.entity.Booking;
import fr.efrei.bookingservice.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Booking> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Booking> findByListingId(UUID listingId);

    List<Booking> findByListingIdAndOwnerId(UUID listingId, UUID ownerId);

    List<Booking> findByListingIdAndStatus(UUID listingId, BookingStatus status);

    List<Booking> findByListingIdAndStatusIn(UUID listingId, List<BookingStatus> statuses);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM Booking b
            WHERE b.listingId = :listingId
            AND b.status IN ('PENDING', 'CONFIRMED')
            AND b.checkInDate < :checkOut
            AND b.checkOutDate > :checkIn
            """)
    boolean existsOverlapping(
            @Param("listingId") UUID listingId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}
