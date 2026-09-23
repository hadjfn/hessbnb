package fr.efrei.bookingservice.service;

import fr.efrei.bookingservice.dto.BookingCreateRequest;
import fr.efrei.bookingservice.dto.BookingResponse;
import fr.efrei.bookingservice.entity.Booking;
import fr.efrei.bookingservice.domain.BookingStatus;
import fr.efrei.bookingservice.exception.BookingNotFoundException;
import fr.efrei.bookingservice.mapper.BookingMapper;
import fr.efrei.bookingservice.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import fr.efrei.bookingservice.port.BookingEvents;
import fr.efrei.bookingservice.domain.BookingPolicy;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final BookingEvents bookingEvents;
    private final Clock clock;

    public BookingResponse getById(UUID id, UUID actorId) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        BookingPolicy.requireParticipant(booking.getTenantId(), booking.getOwnerId(), actorId);
        return bookingMapper.toResponse(booking);
    }

    public List<BookingResponse> getByTenantId(UUID tenantId) {
        return bookingRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<BookingResponse> getByOwnerId(UUID ownerId) {
        return bookingRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public List<BookingResponse> getByListingId(UUID listingId, UUID ownerId) {
        return bookingRepository.findByListingIdAndOwnerId(listingId, ownerId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Transactional
    public BookingResponse create(UUID tenantId, BookingCreateRequest request) {
        BookingPolicy.validateStay(tenantId, request.ownerId(), request.checkInDate(), request.checkOutDate(),
                request.guestsCount(), request.totalPrice(), LocalDate.now(clock));

        if (bookingRepository.existsOverlapping(request.listingId(), request.checkInDate(), request.checkOutDate())) {
            throw new IllegalStateException("This listing is already booked for the selected dates");
        }

        Booking booking = bookingMapper.toEntity(request);
        booking.setTenantId(tenantId);
        Booking saved = bookingRepository.saveAndFlush(booking);

        bookingEvents.record("created", bookingMapper.toResponse(saved), null);

        return bookingMapper.toResponse(saved);
    }

    @Transactional
    public BookingResponse confirm(UUID id, UUID ownerId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        BookingPolicy.requireOwner(booking.getOwnerId(), ownerId);
        booking.setStatus(BookingPolicy.confirm(booking.getStatus()));
        Booking saved = bookingRepository.saveAndFlush(booking);

        bookingEvents.record("confirmed", bookingMapper.toResponse(saved), null);

        return bookingMapper.toResponse(saved);
    }

    @Transactional
    public void cancelAllByListing(UUID listingId, UUID ownerId) {
        List<Booking> active = bookingRepository.findByListingIdAndStatusIn(
                listingId, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        for (Booking booking : active) {
            BookingPolicy.requireOwner(booking.getOwnerId(), ownerId);
            booking.setStatus(BookingPolicy.cancel(booking.getStatus()));
            bookingRepository.saveAndFlush(booking);
            bookingEvents.record("cancelled", bookingMapper.toResponse(booking), "OWNER");
        }
    }

    @Transactional
    public BookingResponse cancel(UUID id, UUID userId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        BookingPolicy.requireParticipant(booking.getTenantId(), booking.getOwnerId(), userId);
        booking.setStatus(BookingPolicy.cancel(booking.getStatus()));
        Booking saved = bookingRepository.saveAndFlush(booking);

        boolean cancelledByOwner = booking.getOwnerId().equals(userId);
        bookingEvents.record("cancelled", bookingMapper.toResponse(saved), cancelledByOwner ? "OWNER" : "TENANT");

        return bookingMapper.toResponse(saved);
    }

}
