package com.weaveyourtrip.service;

import com.weaveyourtrip.model.FlightSuggestion;
import com.weaveyourtrip.model.WizardInput;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds Skyscanner / Booking.com deep-link search URLs from a {@link WizardInput}.
 *
 * <p>No API keys, no affiliate parameters yet — pure URL templating. Affiliate
 * parameters (Skyscanner {@code associateid=}, Booking {@code aid=}) get added
 * in v1.1 once accounts are approved.
 */
@Service
public class BookingUrlService {

    private static final DateTimeFormatter SKYSCANNER_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    /**
     * Skyscanner search URL — uses the flight's specific airports if present,
     * otherwise falls back to a city-level search using the destination.
     *
     * <pre>
     * https://www.skyscanner.net/transport/flights/{from}/{to}/{depart YYMMDD}/{return YYMMDD}/
     *   ?adults={N}&cabinclass={class}
     * </pre>
     */
    public String flightSearch(WizardInput input, FlightSuggestion flight) {
        String from = flight != null && flight.departureAirport() != null
                ? flight.departureAirport().toLowerCase(Locale.ROOT)
                : "anywhere";
        String to = flight != null && flight.arrivalAirport() != null
                ? flight.arrivalAirport().toLowerCase(Locale.ROOT)
                : safeUrlSegment(input.getDestination());

        String depart = input.getStartDate() != null
                ? input.getStartDate().format(SKYSCANNER_DATE) : "";
        String back = input.getEndDate() != null
                ? input.getEndDate().format(SKYSCANNER_DATE) : "";

        String cabin = flight != null && flight.cabinClass() != null
                ? flight.cabinClass() : "economy";

        return "https://www.skyscanner.net/transport/flights/%s/%s/%s/%s/?adults=%d&cabinclass=%s"
                .formatted(from, to, depart, back, input.getAdults(), cabin);
    }

    /**
     * Booking.com search URL — pre-fills destination, check-in/out, and group
     * composition. Booking handles the rest.
     */
    public String hotelSearch(WizardInput input) {
        String ss = urlEncode(input.getDestination());
        String checkin = input.getStartDate() != null ? input.getStartDate().toString() : "";
        String checkout = input.getEndDate() != null ? input.getEndDate().toString() : "";

        return "https://www.booking.com/searchresults.html"
                + "?ss=" + ss
                + "&checkin=" + checkin
                + "&checkout=" + checkout
                + "&group_adults=" + input.getAdults()
                + "&group_children=" + input.getChildren()
                + "&no_rooms=1";
    }

    private static String safeUrlSegment(String s) {
        if (s == null) return "anywhere";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String urlEncode(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
