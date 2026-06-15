package com.weaveyourtrip.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Nominatim (OpenStreetMap) geocoding wrapper with Caffeine cache + soft
 * rate limit. Used by {@link com.weaveyourtrip.service.ItineraryService} (via
 * a post-AI step in a later day) to validate / correct AI-supplied lat/lng
 * coordinates.
 *
 * <p>Nominatim usage policy: max 1 req/sec, must set User-Agent. We respect both.
 *
 * <p>For Day 8 the geocoder is wired but only used opportunistically — full
 * validation pass across all activities lands when streaming is added (so we
 * don't block the user on a slow Nominatim round-trip during synchronous
 * generation).
 */
@Service
@Slf4j
public class GeocodingService {

    private final RestClient client;
    private final Cache<String, Optional<Coords>> cache;
    private final long rateLimitMs;
    private volatile long lastCallMs;

    public GeocodingService(
            @Value("${weaveyourtrip.nominatim.base-url}") String baseUrl,
            @Value("${weaveyourtrip.nominatim.rate-limit-ms:1100}") long rateLimitMs,
            @Value("${weaveyourtrip.nominatim.user-agent}") String userAgent) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(30))
                .maximumSize(10_000)
                .build();
        this.rateLimitMs = rateLimitMs;
    }

    /**
     * Resolve {@code "name, location"} to coordinates. Returns empty when the
     * query yields no result. Cached per query string.
     */
    public Optional<Coords> geocode(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        return cache.get(query.trim().toLowerCase(), this::fetch);
    }

    /**
     * Pure-function bounding-box check — true when {@code (lat, lng)} falls
     * within a reasonable expected box for the named destination. Used to
     * spot AI-hallucinated coordinates without a network call.
     *
     * <p>MVP: hard-coded boxes for the two supported corridor destinations.
     * Move to a config map / DB table when destinations multiply.
     */
    public boolean isWithinExpectedBox(String destination, double lat, double lng) {
        if (destination == null) return true;       // permissive default
        String d = destination.toUpperCase();
        return switch (d) {
            // Schengen ≈ continental Europe envelope
            case "SCHENGEN" -> lat >= 35.0 && lat <= 71.0 && lng >= -10.0 && lng <= 32.0;
            // UK envelope
            case "GB"       -> lat >= 49.5 && lat <= 61.0 && lng >= -8.5 && lng <= 2.0;
            default -> true;                        // unknown corridor → trust AI
        };
    }

    private Optional<Coords> fetch(String query) {
        respectRateLimit();
        try {
            NominatimResult[] results = client.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(NominatimResult[].class);

            if (results == null || results.length == 0) {
                log.debug("Nominatim returned no results for {}", query);
                return Optional.empty();
            }
            NominatimResult r = results[0];
            return Optional.of(new Coords(
                    Double.parseDouble(r.lat),
                    Double.parseDouble(r.lon)));
        } catch (Exception e) {
            log.warn("Nominatim lookup failed for {}: {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized void respectRateLimit() {
        long now = System.currentTimeMillis();
        long sinceLast = now - lastCallMs;
        if (sinceLast < rateLimitMs) {
            try {
                Thread.sleep(rateLimitMs - sinceLast);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallMs = System.currentTimeMillis();
    }

    public record Coords(double lat, double lng) {
    }

    /** Subset of the Nominatim JSON response we need. */
    record NominatimResult(String lat, String lon, String display_name) {
    }
}
