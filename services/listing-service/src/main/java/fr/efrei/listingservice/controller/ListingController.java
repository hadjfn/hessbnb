package fr.efrei.listingservice.controller;

import fr.efrei.listingservice.dto.*;
import fr.efrei.listingservice.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @GetMapping
    public List<ListingResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer maxGuests) {
        return listingService.search(new ListingSearchCriteria(city, propertyType, minPrice, maxPrice, maxGuests));
    }

    @GetMapping("/{id}")
    public ListingResponse getById(@PathVariable UUID id) {
        return listingService.getById(id);
    }

    @GetMapping("/my")
    public List<ListingResponse> getMyListings(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return listingService.getByOwnerId(ownerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ListingCreateRequest request) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return listingService.create(ownerId, request);
    }

    @PutMapping("/{id}")
    public ListingResponse update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ListingUpdateRequest request) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return listingService.update(id, ownerId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        listingService.delete(id, ownerId, jwt.getTokenValue());
    }
}
