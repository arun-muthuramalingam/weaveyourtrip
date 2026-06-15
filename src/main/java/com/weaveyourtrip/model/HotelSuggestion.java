package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI-suggested hotel — illustrative only. {@link #bookingSearchUrl()} points
 * to a Booking.com search built from the wizard inputs (no booking-API
 * integration in MVP v1.0).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelSuggestion(
        String id,
        String name,
        String neighborhood,
        String tier,                  // hostel | guesthouse | mid | boutique | luxury
        double rating,
        int pricePerNightLocal,
        int totalPriceLocal,
        List<String> amenities,
        String bookingSearchUrl
) {
    public HotelSuggestion withIdAndUrl(String newId, String newUrl) {
        return new HotelSuggestion(newId, name, neighborhood, tier, rating,
                pricePerNightLocal, totalPriceLocal, amenities, newUrl);
    }
}
