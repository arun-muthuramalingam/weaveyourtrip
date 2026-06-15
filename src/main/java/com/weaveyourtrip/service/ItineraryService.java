package com.weaveyourtrip.service;

import com.weaveyourtrip.model.Activity;
import com.weaveyourtrip.model.FlightSuggestion;
import com.weaveyourtrip.model.HotelSuggestion;
import com.weaveyourtrip.model.Itinerary;
import com.weaveyourtrip.model.ItineraryContent;
import com.weaveyourtrip.model.ItineraryDay;
import com.weaveyourtrip.model.VisaRequirement;
import com.weaveyourtrip.model.WizardInput;
import com.weaveyourtrip.repository.ItineraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Orchestrates generating + persisting an {@link Itinerary}: looks up the visa
 * context, calls {@link AiService}, enriches flight/hotel suggestions with
 * server-side IDs and Skyscanner / Booking URLs, computes totals, persists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItineraryService {

    private final AiService aiService;
    private final VisaService visaService;
    private final BookingUrlService bookingUrlService;
    private final CurrencyService currencyService;
    private final GeocodingService geocodingService;
    private final ItineraryRepository itineraryRepo;

    @Transactional
    public Itinerary generate(WizardInput input) {
        VisaRequirement visa = visaService
                .lookup(input.getPassport(), input.getDestination())
                .orElse(null);

        ItineraryContent content = aiService.generate(input, visa);

        // Cheap bounding-box sanity check on AI-supplied coords (no network calls).
        // Suspect coords get logged but not replaced — Day 9+ uses Nominatim for true fix.
        validateActivityCoords(content.days(), visa);

        List<FlightSuggestion> enrichedFlights = enrichFlights(
                input.isWantFlights() ? content.flights() : Collections.emptyList(), input);
        List<HotelSuggestion> enrichedHotels = enrichHotels(
                input.isWantHotels() ? content.hotels() : Collections.emptyList(), input);

        int totalLocal = computeTotalLocal(content.days(), enrichedFlights, enrichedHotels);
        String localCcy = currencyService.currencyFor(input.getPassport());
        int totalEur = (int) currencyService.convert(totalLocal, localCcy, "EUR");

        Itinerary itinerary = Itinerary.builder()
                .id(UUID.randomUUID())
                .passport(input.getPassport().name())
                .destination(input.getDestination())
                .startDate(input.getStartDate())
                .endDate(input.getEndDate())
                .tripMode(input.getMode().name())
                .input(input)
                .days(content.days())
                .flights(enrichedFlights)
                .hotels(enrichedHotels)
                .visa(visa)
                .selectedFlightId(firstId(enrichedFlights))
                .selectedHotelId(firstId(enrichedHotels))
                .totalCostLocal(totalLocal)
                .totalCostEur(totalEur)
                .generatedAt(Instant.now())
                .build();

        return itineraryRepo.save(itinerary);
    }

    @Transactional(readOnly = true)
    public Optional<Itinerary> findById(UUID id) {
        return itineraryRepo.findById(id);
    }

    /**
     * Create a placeholder Itinerary before streaming starts. The skeleton has
     * the input + visa snapshot but no days/flights/hotels yet — the streaming
     * endpoint fills those in as the AI emits them.
     */
    @Transactional
    public Itinerary createPending(WizardInput input) {
        VisaRequirement visa = visaService
                .lookup(input.getPassport(), input.getDestination())
                .orElse(null);

        Itinerary skeleton = Itinerary.builder()
                .id(UUID.randomUUID())
                .passport(input.getPassport().name())
                .destination(input.getDestination())
                .startDate(input.getStartDate())
                .endDate(input.getEndDate())
                .tripMode(input.getMode().name())
                .input(input)
                .visa(visa)
                .totalCostLocal(0)
                .totalCostEur(0)
                .generatedAt(Instant.now())
                .build();

        return itineraryRepo.save(skeleton);
    }

    /** Returns true if the itinerary has not yet been streamed in (no days). */
    public boolean isPending(Itinerary it) {
        return it.getDays() == null || it.getDays().isEmpty();
    }

    /**
     * Persist the final state of an itinerary that was streamed in. Called by
     * the SSE endpoint when the AI emits its {@code "done"} line.
     */
    @Transactional
    public Itinerary completeFromStream(UUID id,
                                        List<ItineraryDay> days,
                                        List<FlightSuggestion> flights,
                                        List<HotelSuggestion> hotels) {
        Itinerary it = itineraryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + id));

        WizardInput input = it.getInput();
        List<FlightSuggestion> enrichedFlights = enrichFlights(
                input.isWantFlights() ? flights : Collections.emptyList(), input);
        List<HotelSuggestion> enrichedHotels = enrichHotels(
                input.isWantHotels() ? hotels : Collections.emptyList(), input);

        it.setDays(days);
        it.setFlights(enrichedFlights);
        it.setHotels(enrichedHotels);
        it.setSelectedFlightId(firstId(enrichedFlights));
        it.setSelectedHotelId(firstId(enrichedHotels));

        validateActivityCoords(days, it.getVisa());
        recomputeTotals(it);
        return itineraryRepo.save(it);
    }

    /** Update the selected flight on an existing itinerary. */
    @Transactional
    public void selectFlight(UUID itineraryId, String flightId) {
        Itinerary it = itineraryRepo.findById(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + itineraryId));
        if (it.getFlights() == null
                || it.getFlights().stream().noneMatch(f -> flightId.equals(f.id()))) {
            throw new IllegalArgumentException("Flight not in this itinerary: " + flightId);
        }
        it.setSelectedFlightId(flightId);
        recomputeTotals(it);
        itineraryRepo.save(it);
    }

    /** Update the selected hotel on an existing itinerary. */
    @Transactional
    public void selectHotel(UUID itineraryId, String hotelId) {
        Itinerary it = itineraryRepo.findById(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + itineraryId));
        if (it.getHotels() == null
                || it.getHotels().stream().noneMatch(h -> hotelId.equals(h.id()))) {
            throw new IllegalArgumentException("Hotel not in this itinerary: " + hotelId);
        }
        it.setSelectedHotelId(hotelId);
        recomputeTotals(it);
        itineraryRepo.save(it);
    }

    /** Recompute total cost (local + EUR) whenever the selected flight or hotel changes. */
    private void recomputeTotals(Itinerary it) {
        int total = 0;
        if (it.getDays() != null) {
            total += it.getDays().stream().mapToInt(ItineraryDay::estimatedDailyCostLocal).sum();
        }
        if (it.getFlights() != null && it.getSelectedFlightId() != null) {
            total += it.getFlights().stream()
                    .filter(f -> it.getSelectedFlightId().equals(f.id()))
                    .findFirst()
                    .map(FlightSuggestion::priceLocal)
                    .orElse(0);
        }
        if (it.getHotels() != null && it.getSelectedHotelId() != null) {
            total += it.getHotels().stream()
                    .filter(h -> it.getSelectedHotelId().equals(h.id()))
                    .findFirst()
                    .map(HotelSuggestion::totalPriceLocal)
                    .orElse(0);
        }
        it.setTotalCostLocal(total);

        // Cross-check the EUR total against the same FX rate so the two stay aligned
        String localCcy = currencyService.currencyFor(
                com.weaveyourtrip.model.Passport.valueOf(it.getPassport()));
        it.setTotalCostEur((int) currencyService.convert(total, localCcy, "EUR"));
    }

    /**
     * Lightweight coord validation — logs warnings when AI activity coords fall
     * outside the destination's expected bounding box. No network calls; the
     * real Nominatim fix-up runs Day 9+ once streaming hides latency.
     */
    private void validateActivityCoords(List<ItineraryDay> days, VisaRequirement visa) {
        if (days == null || visa == null) return;
        for (ItineraryDay day : days) {
            if (day.activities() == null) continue;
            for (Activity act : day.activities()) {
                if (!geocodingService.isWithinExpectedBox(visa.destination(), act.lat(), act.lng())) {
                    log.warn("Suspect coords for activity '{}' at {}, {} (destination={})",
                            act.name(), act.lat(), act.lng(), visa.destination());
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Server-side enrichment of AI suggestions
    // ───────────────────────────────────────────────────────────────────────

    private List<FlightSuggestion> enrichFlights(List<FlightSuggestion> raw, WizardInput input) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        return IntStream.range(0, raw.size())
                .mapToObj(i -> raw.get(i).withIdAndUrl(
                        "flight-" + i,
                        bookingUrlService.flightSearch(input, raw.get(i))))
                .toList();
    }

    private List<HotelSuggestion> enrichHotels(List<HotelSuggestion> raw, WizardInput input) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        String url = bookingUrlService.hotelSearch(input);
        return IntStream.range(0, raw.size())
                .mapToObj(i -> raw.get(i).withIdAndUrl("hotel-" + i, url))
                .toList();
    }

    private int computeTotalLocal(List<ItineraryDay> days,
                                  List<FlightSuggestion> flights,
                                  List<HotelSuggestion> hotels) {
        int total = 0;
        if (days != null) {
            total += days.stream().mapToInt(ItineraryDay::estimatedDailyCostLocal).sum();
        }
        if (flights != null && !flights.isEmpty()) {
            total += flights.get(0).priceLocal();  // selected = first by default
        }
        if (hotels != null && !hotels.isEmpty()) {
            total += hotels.get(0).totalPriceLocal();
        }
        return total;
    }

    private static String firstId(List<? extends Object> list) {
        if (list == null || list.isEmpty()) return null;
        Object first = list.get(0);
        if (first instanceof FlightSuggestion f) return f.id();
        if (first instanceof HotelSuggestion h) return h.id();
        return null;
    }
}
