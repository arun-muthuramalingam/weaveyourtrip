package com.weaveyourtrip.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Single line item on a visa application document checklist.
 * Loaded from per-corridor JSON in {@code resources/visa-data/}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisaDocument(
        String id,
        String name,
        String notes
) {
}
