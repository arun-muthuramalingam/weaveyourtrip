package com.weaveyourtrip.controller;

import com.weaveyourtrip.model.Itinerary;
import com.weaveyourtrip.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Flight + hotel selection pages. Users land here from the "Change" link in
 * the itinerary view's booking strip; choosing an option redirects back to
 * the itinerary with the new selection persisted.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/itinerary/{id}")
public class SelectionController {

    private final ItineraryService itineraryService;

    // ───────────────────────────────────────────────────────────────────────
    // Flights
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/flights")
    public String flightsPage(@PathVariable UUID id, Model model) {
        Itinerary itinerary = loadItinerary(id);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("flights", itinerary.getFlights());
        return "flights";
    }

    @PostMapping("/select-flight")
    public String selectFlight(@PathVariable UUID id, @RequestParam String flightId) {
        itineraryService.selectFlight(id, flightId);
        return "redirect:/itinerary/" + id;
    }

    // ───────────────────────────────────────────────────────────────────────
    // Hotels
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/hotels")
    public String hotelsPage(@PathVariable UUID id, Model model) {
        Itinerary itinerary = loadItinerary(id);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("hotels", itinerary.getHotels());
        return "hotels";
    }

    @PostMapping("/select-hotel")
    public String selectHotel(@PathVariable UUID id, @RequestParam String hotelId) {
        itineraryService.selectHotel(id, hotelId);
        return "redirect:/itinerary/" + id;
    }

    // ───────────────────────────────────────────────────────────────────────

    private Itinerary loadItinerary(UUID id) {
        return itineraryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Itinerary not found: " + id));
    }
}
