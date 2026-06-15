package com.weaveyourtrip.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaveyourtrip.model.Itinerary;
import com.weaveyourtrip.model.ItineraryDay;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.service.CurrencyService;
import com.weaveyourtrip.service.ItineraryService;
import com.weaveyourtrip.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders persisted itineraries. The wizard's {@code /api/generate} redirects
 * here on success.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ItineraryController {

    /** Activity type → emoji icon. Shared with {@link StreamController}.
     *  Could move to application.yml or per-mode customisation later. */
    public static final Map<String, String> ICON_FOR_TYPE = Map.of(
            "sightseeing", "🏛️",
            "food",        "🍽️",
            "transport",   "🚆",
            "shopping",    "🛍️",
            "nature",      "🌿",
            "religious",   "⛪",
            "nightlife",   "🎉",
            "photography", "📸"
    );

    private final ItineraryService itineraryService;
    private final CurrencyService currencyService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;

    @GetMapping("/itinerary/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Itinerary itinerary = itineraryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Itinerary not found: " + id));

        String localCcy = currencyService.currencyFor(Passport.valueOf(itinerary.getPassport()));
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("iconForType", ICON_FOR_TYPE);
        model.addAttribute("localCurrency", localCcy);
        model.addAttribute("currencySymbol", currencyService.symbol(localCcy));
        model.addAttribute("activitiesJsonByDay", buildActivitiesJson(itinerary.getDays()));
        // True while AI is still streaming — the template renders an empty
        // container + EventSource to receive day-card HTML over SSE.
        model.addAttribute("streamingMode", itineraryService.isPending(itinerary));

        // Convenience attributes the template reads directly
        if (itinerary.getFlights() != null && itinerary.getSelectedFlightId() != null) {
            itinerary.getFlights().stream()
                    .filter(f -> itinerary.getSelectedFlightId().equals(f.id()))
                    .findFirst()
                    .ifPresent(f -> model.addAttribute("selectedFlight", f));
        }
        if (itinerary.getHotels() != null && itinerary.getSelectedHotelId() != null) {
            itinerary.getHotels().stream()
                    .filter(h -> itinerary.getSelectedHotelId().equals(h.id()))
                    .findFirst()
                    .ifPresent(h -> model.addAttribute("selectedHotel", h));
        }

        return "itinerary";
    }

    // ───────────────────────────────────────────────────────────────────────
    // PDF exports — Day 10
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/itinerary/{id}/pdf")
    public ResponseEntity<byte[]> itineraryPdf(@PathVariable UUID id) {
        Itinerary itinerary = itineraryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Itinerary not found: " + id));
        if (itineraryService.isPending(itinerary)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Itinerary still generating — try again in a moment.");
        }

        byte[] pdf = pdfExportService.renderItineraryPdf(itinerary);
        return pdfResponse(pdf, "weaveyourtrip-itinerary-" + id + ".pdf");
    }

    @GetMapping("/itinerary/{id}/visa-checklist.pdf")
    public ResponseEntity<byte[]> visaChecklistPdf(@PathVariable UUID id) {
        Itinerary itinerary = itineraryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Itinerary not found: " + id));
        if (itinerary.getVisa() == null || !itinerary.getVisa().required()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No visa required for this trip — no checklist to generate.");
        }

        byte[] pdf = pdfExportService.renderVisaChecklistPdf(itinerary);
        return pdfResponse(pdf, "weaveyourtrip-visa-checklist-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] body, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * Build a per-day JSON-string map of {@code [{lat, lng, name, time}]}
     * for the Leaflet maps init. Map values land as escaped strings inside
     * {@code data-activities} attributes; the client-side JS parses them.
     */
    private Map<Integer, String> buildActivitiesJson(List<ItineraryDay> days) {
        Map<Integer, String> out = new HashMap<>();
        if (days == null) return out;
        for (ItineraryDay day : days) {
            if (day.activities() == null) continue;
            List<Map<String, Object>> pins = day.activities().stream()
                    .map(a -> Map.<String, Object>of(
                            "lat",  a.lat(),
                            "lng",  a.lng(),
                            "name", a.name() != null ? a.name() : "",
                            "time", a.time() != null ? a.time() : ""))
                    .toList();
            try {
                out.put(day.dayNumber(), objectMapper.writeValueAsString(pins));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialise activities for day {}: {}", day.dayNumber(), e.getMessage());
                out.put(day.dayNumber(), "[]");
            }
        }
        return out;
    }
}
