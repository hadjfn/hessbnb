package fr.efrei.bookingservice.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BookingCreateRequest(
        @NotNull
        UUID listingId,

        @NotNull
        UUID ownerId,

        @NotNull @FutureOrPresent
        LocalDate checkInDate,

        @NotNull @FutureOrPresent
        LocalDate checkOutDate,

        @NotNull @Min(1)
        Integer guestsCount,

        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal totalPrice,

        String message
) {}
