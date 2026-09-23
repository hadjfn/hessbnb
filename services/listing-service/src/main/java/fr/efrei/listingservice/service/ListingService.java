package fr.efrei.listingservice.service;

import fr.efrei.listingservice.config.RabbitMQConfig;
import fr.efrei.listingservice.dto.*;
import fr.efrei.listingservice.entity.Listing;
import fr.efrei.listingservice.entity.ListingStatus;
import fr.efrei.listingservice.entity.PropertyType;
import fr.efrei.listingservice.exception.ListingNotFoundException;
import fr.efrei.listingservice.mapper.ListingMapper;
import fr.efrei.listingservice.repository.ListingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final RestClient bookingClient;
    private final RabbitTemplate rabbitTemplate;

    public ListingService(ListingRepository listingRepository,
                          ListingMapper listingMapper,
                          RabbitTemplate rabbitTemplate,
                          @Value("${services.booking-url}") String bookingUrl) {
        this.listingRepository = listingRepository;
        this.listingMapper = listingMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.bookingClient = RestClient.builder().baseUrl(bookingUrl).build();
    }

    public List<ListingResponse> search(ListingSearchCriteria criteria) {
        PropertyType type = null;
        if (criteria.propertyType() != null && !criteria.propertyType().isBlank()) {
            type = PropertyType.valueOf(criteria.propertyType().toUpperCase());
        }
        return listingRepository.search(
                criteria.city(), type,
                criteria.minPrice(), criteria.maxPrice(),
                criteria.maxGuests()
        ).stream().map(listingMapper::toResponse).toList();
    }

    public ListingResponse getById(UUID id) {
        return listingRepository.findById(id)
                .map(listingMapper::toResponse)
                .orElseThrow(() -> new ListingNotFoundException(id));
    }

    public List<ListingResponse> getByOwnerId(UUID ownerId) {
        return listingRepository.findByOwnerIdAndStatusNot(ownerId, ListingStatus.DELETED).stream()
                .map(listingMapper::toResponse)
                .toList();
    }

    @Transactional
    public ListingResponse create(UUID ownerId, ListingCreateRequest request) {
        Listing listing = listingMapper.toEntity(request);
        listing.setOwnerId(ownerId);

        if (request.amenities() != null) {
            request.amenities().forEach(listing::addAmenity);
        }
        if (request.images() != null) {
            request.images().forEach(img ->
                    listing.addImage(img.imageUrl(), img.primary(), img.sortOrder()));
        }

        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Transactional
    public ListingResponse update(UUID id, UUID ownerId, ListingUpdateRequest request) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException(id));

        if (!listing.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("You can only update your own listings");
        }

        if (request.title() != null) listing.setTitle(request.title());
        if (request.description() != null) listing.setDescription(request.description());
        if (request.propertyType() != null) listing.setPropertyType(PropertyType.valueOf(request.propertyType().toUpperCase()));
        if (request.address() != null) listing.setAddress(request.address());
        if (request.city() != null) listing.setCity(request.city());
        if (request.country() != null) listing.setCountry(request.country());
        if (request.zipCode() != null) listing.setZipCode(request.zipCode());
        if (request.latitude() != null) listing.setLatitude(request.latitude());
        if (request.longitude() != null) listing.setLongitude(request.longitude());
        if (request.pricePerNight() != null) listing.setPricePerNight(request.pricePerNight());
        if (request.maxGuests() != null) listing.setMaxGuests(request.maxGuests());
        if (request.bedrooms() != null) listing.setBedrooms(request.bedrooms());
        if (request.bathrooms() != null) listing.setBathrooms(request.bathrooms());
        if (request.surfaceArea() != null) listing.setSurfaceArea(request.surfaceArea());

        if (request.amenities() != null) {
            listing.getAmenities().clear();
            request.amenities().forEach(listing::addAmenity);
        }
        if (request.images() != null) {
            listing.getImages().clear();
            request.images().forEach(img ->
                    listing.addImage(img.imageUrl(), img.primary(), img.sortOrder()));
        }

        return listingMapper.toResponse(listingRepository.save(listing));
    }

    @Transactional
    public void delete(UUID id, UUID ownerId, String bearerToken) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException(id));

        if (!listing.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("You can only delete your own listings");
        }

        Set<UUID> affectedTenants = new HashSet<>();
        try {
            List<Map<String, Object>> bookings = bookingClient.get()
                    .uri("/api/bookings/listing/{listingId}", id)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (bookings != null) {
                for (Map<String, Object> booking : bookings) {
                    String status = (String) booking.get("status");
                    if ("PENDING".equals(status) || "CONFIRMED".equals(status)) {
                        String tenantId = (String) booking.get("tenantId");
                        if (tenantId != null) {
                            affectedTenants.add(UUID.fromString(tenantId));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer les réservations du listing {}: {}", id, e.getMessage());
        }

        try {
            bookingClient.patch()
                    .uri("/api/bookings/listing/{listingId}/cancel-all", id)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot delete listing before its bookings are cancelled", e);
        }

        listing.setStatus(ListingStatus.DELETED);
        listingRepository.save(listing);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.LISTING_DELETED_KEY,
                    Map.of(
                            "listingId", id.toString(),
                            "ownerId", ownerId.toString(),
                            "title", listing.getTitle(),
                            "affectedTenantIds", affectedTenants.stream().map(UUID::toString).toList()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to publish listing.deleted event: {}", e.getMessage());
        }
    }
}
