package com.weaveyourtrip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.model.VisaRequirement;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads hand-curated visa corridor data from {@code resources/visa-data/*.json}
 * at application startup and provides lookup + earliest-viable-departure
 * calculations.
 *
 * <p>Each JSON file is a {@link VisaRequirement} for one {@code (passport,
 * destination)} pair. Files are keyed by the {@code passport} and
 * {@code destination} fields inside the JSON, not by filename — so renaming
 * files won't break lookups.
 */
@Service
@Slf4j
public class VisaService {

    private final ResourcePatternResolver resolver;
    private final ObjectMapper mapper;
    private final String visaDataPattern;
    private final int bufferDays;
    private final Map<CorridorKey, VisaRequirement> corridors = new ConcurrentHashMap<>();

    public VisaService(
            ObjectMapper mapper,
            @Value("${weaveyourtrip.visa-data-path}") String visaDataPath,
            @Value("${weaveyourtrip.visa-buffer-days:7}") int bufferDays) {
        this(new PathMatchingResourcePatternResolver(), mapper, visaDataPath, bufferDays);
    }

    /**
     * Constructor for tests — supply a custom resolver so resources can be loaded
     * from arbitrary locations without a full Spring context.
     */
    VisaService(ResourcePatternResolver resolver,
                ObjectMapper mapper,
                String visaDataPath,
                int bufferDays) {
        this.resolver = resolver;
        this.mapper = mapper;
        this.visaDataPattern = visaDataPath.endsWith("/") ? visaDataPath + "*.json" : visaDataPath + "/*.json";
        this.bufferDays = bufferDays;
    }

    @PostConstruct
    void loadCorridors() throws IOException {
        Resource[] files = resolver.getResources(visaDataPattern);
        for (Resource res : files) {
            try (InputStream in = res.getInputStream()) {
                VisaRequirement req = mapper.readValue(in, VisaRequirement.class);
                CorridorKey key = new CorridorKey(req.passport(), req.destination());
                corridors.put(key, req);
                log.debug("Loaded visa corridor {} from {}", key, res.getFilename());
            } catch (IOException e) {
                log.error("Failed to load visa data from {}: {}", res.getFilename(), e.getMessage());
                throw e;
            }
        }
        log.info("Loaded {} visa corridors from {}", corridors.size(), visaDataPattern);
    }

    /**
     * Look up the visa requirement for a passport going to a destination.
     * The destination string is normalised via {@link #resolveDestination(String)},
     * so users can type "Paris", "France", or "SCHENGEN" interchangeably.
     */
    public Optional<VisaRequirement> lookup(Passport passport, String destination) {
        if (passport == null || destination == null) return Optional.empty();
        String resolved = resolveDestination(destination);
        return Optional.ofNullable(corridors.get(new CorridorKey(passport, resolved)));
    }

    /**
     * Earliest date a traveller can realistically board their flight, accounting
     * for typical visa processing time + a safety buffer. Returns {@code today}
     * unchanged when no visa is required.
     */
    public LocalDate earliestViableDeparture(VisaRequirement req, LocalDate today) {
        if (req == null || !req.required() || req.processingDays() == null) {
            return today;
        }
        return today.plusDays(req.processingDays().typical() + bufferDays);
    }

    /**
     * Map a destination string typed by the user (city, country, alias) to the
     * corridor identifier used in the visa JSON files. MVP-level: hand-coded
     * Schengen + UK aliases; pass-through everything else as uppercase.
     */
    public String resolveDestination(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "france", "italy", "germany", "spain", "netherlands",
                 "switzerland", "austria", "portugal", "greece",
                 "schengen", "europe",
                 "paris", "rome", "berlin", "barcelona", "amsterdam", "vienna"
                    -> "SCHENGEN";
            case "uk", "united kingdom", "england", "britain", "scotland",
                 "great britain", "london", "edinburgh", "manchester"
                    -> "GB";
            default -> input.toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Returns all loaded corridors — useful for diagnostics and the {@code
     * /actuator}-style listing of supported (passport, destination) pairs.
     */
    public Collection<VisaRequirement> allCorridors() {
        return corridors.values();
    }

    public record CorridorKey(Passport passport, String destination) {
    }
}
