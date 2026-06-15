package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A single activity within an {@link ItineraryDay}. AI-generated, with coords
 * for the Leaflet map (Day 7+).
 *
 * <p>Activities are passport-agnostic — no field on this record references the
 * traveller's passport. This is a deliberate architectural choice (see plan
 * §20 mixed-passport moat) so the v3 group-planning feature can wrap multiple
 * travellers around a shared itinerary without refactoring.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Activity(
        String time,                 // e.g. "09:00"
        String type,                 // sightseeing | food | transport | shopping | nature
        String name,
        String description,
        String location,             // neighbourhood / address
        double lat,                  // WGS84 decimal
        double lng,
        int estimatedCostLocal,
        int durationMinutes,
        List<String> tags,
        List<String> dietaryFlags    // veg | halal | jain when type=food
) {
}
