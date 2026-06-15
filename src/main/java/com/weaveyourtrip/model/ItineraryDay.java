package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * One day in a generated itinerary — date, theme, list of activities, and
 * the estimated daily cost in the traveller's local currency.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItineraryDay(
        int dayNumber,
        LocalDate date,
        String theme,
        int estimatedDailyCostLocal,
        List<Activity> activities
) {
}
