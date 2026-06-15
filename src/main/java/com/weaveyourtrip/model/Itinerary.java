package com.weaveyourtrip.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Persistent itinerary aggregate. Maps onto V1's {@code itinerary} table —
 * JSONB columns hold the AI-generated content blocks. Hibernate 6 + the
 * {@code @JdbcTypeCode(SqlTypes.JSON)} annotation handle the JSON ↔ Java
 * conversion via the auto-configured Jackson {@code ObjectMapper}.
 *
 * <p>{@code createdIp} is deliberately omitted at MVP — V1 schema has an
 * {@code INET} column we leave NULL. Rate limiting tracks IPs in-memory via
 * {@link com.weaveyourtrip.service.RateLimiter}.
 */
@Entity
@Table(name = "itinerary")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Itinerary {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String passport;                    // enum name as String — keeps DB queryable

    @Column(nullable = false)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "trip_mode", nullable = false)
    private String tripMode;                    // enum name as String

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private WizardInput input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ItineraryDay> days;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<FlightSuggestion> flights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<HotelSuggestion> hotels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private VisaRequirement visa;

    @Column(name = "selected_flight_id")
    private String selectedFlightId;

    @Column(name = "selected_hotel_id")
    private String selectedHotelId;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Column(name = "total_cost_local")
    private Integer totalCostLocal;

    @Column(name = "total_cost_eur")
    private Integer totalCostEur;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
