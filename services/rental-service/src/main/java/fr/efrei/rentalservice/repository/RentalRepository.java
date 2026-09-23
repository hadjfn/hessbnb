package fr.efrei.rentalservice.repository;

import fr.efrei.rentalservice.entity.Rental;
import fr.efrei.rentalservice.entity.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalRepository extends JpaRepository<Rental, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO rentals(id, booking_id, listing_id, tenant_id, owner_id, start_date, end_date,
                                monthly_rent, total_amount, status, created_at, updated_at)
            VALUES (:id, :bookingId, :listingId, :tenantId, :ownerId, :startDate, :endDate,
                    :monthlyRent, :totalAmount, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (booking_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, UUID bookingId, UUID listingId, UUID tenantId, UUID ownerId,
                       LocalDate startDate, LocalDate endDate, BigDecimal monthlyRent, BigDecimal totalAmount);

    Optional<Rental> findByBookingId(UUID bookingId);

    List<Rental> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Rental> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Rental> findByListingId(UUID listingId);

    List<Rental> findByStatus(RentalStatus status);

    boolean existsByBookingId(UUID bookingId);
}
