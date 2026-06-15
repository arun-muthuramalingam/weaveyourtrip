package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The top-level structured response from the AI: highlights, flights, hotels,
 * and day-by-day breakdown. Spring AI's {@code BeanOutputConverter} generates
 * a JSON schema from this record and appends schema instructions to the prompt.
 *
 * <p>Day 7+ adds totals (totalCostLocal, totalCostEur). For Day 6 those are
 * computed server-side from {@code days}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItineraryContent(
        List<String> highlights,
        List<FlightSuggestion> flights,
        List<HotelSuggestion> hotels,
        List<ItineraryDay> days
) {
}
