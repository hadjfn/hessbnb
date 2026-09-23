package fr.efrei.rentalservice.service;

import fr.efrei.rentalservice.dto.RentalCreateRequest;
import fr.efrei.rentalservice.dto.RentalResponse;
import fr.efrei.rentalservice.entity.Rental;
import fr.efrei.rentalservice.entity.RentalStatus;
import fr.efrei.rentalservice.exception.RentalNotFoundException;
import fr.efrei.rentalservice.mapper.RentalMapper;
import fr.efrei.rentalservice.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalService {

    private final RentalRepository rentalRepository;
    private final RentalMapper rentalMapper;

    public RentalResponse getById(UUID id) {
        return rentalRepository.findById(id)
                .map(rentalMapper::toResponse)
                .orElseThrow(() -> new RentalNotFoundException(id));
    }

    public RentalResponse getByBookingId(UUID bookingId) {
        return rentalRepository.findByBookingId(bookingId)
                .map(rentalMapper::toResponse)
                .orElseThrow(() -> new RentalNotFoundException(bookingId));
    }

    public List<RentalResponse> getByTenantId(UUID tenantId) {
        return rentalRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(rentalMapper::toResponse)
                .toList();
    }

    public List<RentalResponse> getByOwnerId(UUID ownerId) {
        return rentalRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(rentalMapper::toResponse)
                .toList();
    }

    @Transactional
    public RentalResponse create(RentalCreateRequest request) {
        // RabbitMQ delivery is at least once. The database unique key, not a check-then-save,
        // arbitrates concurrent duplicates without resetting an existing rental's status.
        rentalRepository.insertIfAbsent(UUID.randomUUID(), request.bookingId(), request.listingId(),
                request.tenantId(), request.ownerId(), request.startDate(), request.endDate(),
                request.monthlyRent(), request.totalAmount());
        Rental rental = rentalRepository.findByBookingId(request.bookingId())
                .orElseThrow(() -> new IllegalStateException("Rental was not persisted"));
        return rentalMapper.toResponse(rental);
    }

    @Transactional
    public RentalResponse complete(UUID id, UUID ownerId) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new RentalNotFoundException(id));

        if (!rental.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("Only the owner can complete a rental");
        }
        if (rental.getStatus() != RentalStatus.ACTIVE) {
            throw new IllegalStateException("Only active rentals can be completed");
        }

        rental.setStatus(RentalStatus.COMPLETED);
        return rentalMapper.toResponse(rentalRepository.save(rental));
    }

    @Transactional
    public RentalResponse terminate(UUID id, UUID userId) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new RentalNotFoundException(id));

        if (!rental.getTenantId().equals(userId) && !rental.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("Only the tenant or owner can terminate a rental");
        }
        if (rental.getStatus() != RentalStatus.ACTIVE) {
            throw new IllegalStateException("Only active rentals can be terminated");
        }

        rental.setStatus(RentalStatus.TERMINATED);
        return rentalMapper.toResponse(rentalRepository.save(rental));
    }
}
