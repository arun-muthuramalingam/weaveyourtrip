package com.weaveyourtrip.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.model.VisaRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link VisaService} — instantiates the service directly without
 * a Spring context so the test is fast and doesn't need Postgres / Gemini.
 */
class VisaServiceTest {

    private VisaService service;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        service = new VisaService(
                new PathMatchingResourcePatternResolver(),
                mapper,
                "classpath:/visa-data/",
                7);
        service.loadCorridors();
    }

    @Test
    void loadsAllBundledCorridors() {
        assertThat(service.allCorridors())
                .as("MVP launch ships IN-SCHENGEN + IN-GB")
                .hasSize(2);
    }

    @Test
    void lookupSchengenByCountryName() {
        Optional<VisaRequirement> req = service.lookup(Passport.IN, "France");

        assertThat(req).isPresent();
        assertThat(req.get().required()).isTrue();
        assertThat(req.get().destination()).isEqualTo("SCHENGEN");
        assertThat(req.get().feeEur()).isEqualTo(80);
        assertThat(req.get().processingDays().typical()).isEqualTo(15);
        assertThat(req.get().requiredDocuments()).hasSize(9);
    }

    @Test
    void lookupSchengenByCityAlias() {
        assertThat(service.lookup(Passport.IN, "Paris")).isPresent();
        assertThat(service.lookup(Passport.IN, "Amsterdam")).isPresent();
        assertThat(service.lookup(Passport.IN, "Schengen")).isPresent();
    }

    @Test
    void lookupGbByAlias() {
        Optional<VisaRequirement> req = service.lookup(Passport.IN, "United Kingdom");

        assertThat(req).isPresent();
        assertThat(req.get().destination()).isEqualTo("GB");
        assertThat(req.get().feeEur()).isEqualTo(135);
    }

    @Test
    void lookupUnknownCorridorReturnsEmpty() {
        assertThat(service.lookup(Passport.IN, "Japan")).isEmpty();
        assertThat(service.lookup(Passport.US, "France")).isEmpty();   // soft-passport not yet in MVP
    }

    @Test
    void lookupHandlesNullInputs() {
        assertThat(service.lookup(null, "France")).isEmpty();
        assertThat(service.lookup(Passport.IN, null)).isEmpty();
    }

    @Test
    void earliestViableDeparture_addsProcessingPlusBuffer() {
        VisaRequirement req = service.lookup(Passport.IN, "Schengen").orElseThrow();
        LocalDate today = LocalDate.of(2026, 7, 1);

        LocalDate earliest = service.earliestViableDeparture(req, today);

        // typical = 15, buffer = 7 → +22 days
        assertThat(earliest).isEqualTo(LocalDate.of(2026, 7, 23));
    }

    @Test
    void earliestViableDeparture_returnsTodayWhenNoVisaRequired() {
        VisaRequirement noVisa = new VisaRequirement(
                Passport.US, "SCHENGEN", false, "Visa-free travel",
                null, null, null, null, null, null, null, null, null, null, null, null);
        LocalDate today = LocalDate.of(2026, 7, 1);

        assertThat(service.earliestViableDeparture(noVisa, today)).isEqualTo(today);
    }

    @Test
    void resolveDestinationIsCaseInsensitive() {
        assertThat(service.resolveDestination("FRANCE")).isEqualTo("SCHENGEN");
        assertThat(service.resolveDestination("france")).isEqualTo("SCHENGEN");
        assertThat(service.resolveDestination("  Paris  ")).isEqualTo("SCHENGEN");
        assertThat(service.resolveDestination("uk")).isEqualTo("GB");
    }
}
