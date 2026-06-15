package com.weaveyourtrip.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates wizard form state across the 6 steps. Lives as a session-scoped
 * bean while the user works through the wizard; serialised to JSONB on
 * {@code /api/generate}.
 *
 * <p>Mutable by design — each HTMX step PATCH-merges into this bean before
 * advancing. Bean Validation runs at step boundaries and on final submit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WizardInput {

    // ─── Step 1 ──────────────────────────────────────────────────
    @NotNull(message = "Passport is required — pick at step 1")
    private Passport passport;

    // ─── Step 2 ──────────────────────────────────────────────────
    @NotBlank(message = "Destination is required")
    private String destination;

    // ─── Step 3 ──────────────────────────────────────────────────
    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Min(value = 1, message = "At least 1 adult traveller")
    @Max(value = 20, message = "Up to 20 adults per trip")
    private int adults = 1;

    @Min(value = 0)
    @Max(value = 10)
    private int children;

    @Builder.Default
    private List<Integer> childAges = new ArrayList<>();

    @Min(value = 1000, message = "Budget must be at least 1000 (in local currency)")
    private int budgetTotalLocal;

    // ─── Step 4 ──────────────────────────────────────────────────
    @NotNull(message = "Trip mode is required — pick at step 4")
    private TripMode mode;

    // ─── Step 5 ──────────────────────────────────────────────────
    @Builder.Default
    private List<String> interests = new ArrayList<>();

    @Builder.Default
    private List<String> dietary = new ArrayList<>();

    private String accommodationTier;   // hostel | guesthouse | mid | boutique | luxury
    private String pace;                // relaxed | balanced | packed

    // Mode-specific (FAMILY)
    private boolean needsStrollerAccess;
    private boolean showMedicalProximity;

    // Mode-specific (SENIOR)
    private String mobilityLevel;       // active | moderate | limited
    private boolean preferDirectFlights;

    // ─── Step 6 ──────────────────────────────────────────────────
    private boolean wantFlights = true;
    private boolean wantHotels = true;

    /**
     * Convenience: total trip days inclusive of start and end.
     */
    public int totalDays() {
        if (startDate == null || endDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
