package com.jamia.backend.dto;

import com.jamia.backend.entity.ContributionFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The data the app sends to create a savings room.
 * The upper limit of maxMembers depends on the creator's plan (checked in the service).
 */
public record CreateRoomRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Contribution amount is required")
        @DecimalMin(value = "0.01", message = "Contribution amount must be more than 0")
        @Digits(integer = 10, fraction = 2, message = "Contribution amount can have at most 2 decimal places")
        BigDecimal contributionAmount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code, e.g. AED")
        String currency,

        @NotNull(message = "Frequency is required (WEEKLY, BIWEEKLY or MONTHLY)")
        ContributionFrequency frequency,

        @NotNull(message = "Maximum members is required")
        @Min(value = 2, message = "A room needs at least 2 members")
        Integer maxMembers
) {

    // Before validation: tidy the text fields ("aed " -> "AED", empty description -> none).
    public CreateRoomRequest {
        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
        if (description != null && description.isBlank()) {
            description = null;
        }
    }
}
