package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * Visa requirements for a {@code (passport, destination)} corridor.
 * Hand-curated and loaded from {@code resources/visa-data/{PASSPORT}-{DEST}.json}
 * at application startup.
 *
 * <p>For hard-passport corridors (e.g. IN→SCHENGEN), {@link #required()} is true
 * and the full document checklist + processing window is populated. For soft-passport
 * corridors (e.g. US→SCHENGEN), {@link #required()} is false and most fields are null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisaRequirement(
        Passport passport,
        String destination,
        boolean required,
        String type,
        ProcessingDays processingDays,
        Integer feeEur,
        Integer serviceFeeEur,
        Integer validityDays,
        String entries,
        List<String> applyCenters,
        String appointmentAvailability,
        List<VisaDocument> requiredDocuments,
        List<String> commonRejectionReasons,
        String officialSourceUrl,
        LocalDate lastVerified,
        String disclaimer
) {
    public record ProcessingDays(int min, int typical, int max) {
    }
}
