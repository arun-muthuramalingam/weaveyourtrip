package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * AI-suggested flight — illustrative only. {@link #bookingSearchUrl()} points
 * to a Skyscanner search built from the wizard inputs (no booking-API
 * integration in MVP v1.0).
 *
 * <p>The {@code id} field is assigned server-side after AI generation so the
 * AI never has to invent stable IDs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightSuggestion(
        String id,
        String airline,
        String departureAirport,
        String arrivalAirport,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        int stops,
        int durationMinutes,
        int priceLocal,
        int priceEur,
        String cabinClass,
        String bookingSearchUrl
) {
    /**
     * Returns a copy of this suggestion with the given id and booking URL.
     * Used by the server to enrich AI output before persistence.
     */
    public FlightSuggestion withIdAndUrl(String newId, String newUrl) {
        return new FlightSuggestion(newId, airline, departureAirport, arrivalAirport,
                departureTime, arrivalTime, stops, durationMinutes,
                priceLocal, priceEur, cabinClass, newUrl);
    }
}
