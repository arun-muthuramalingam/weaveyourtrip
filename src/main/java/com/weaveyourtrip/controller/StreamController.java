package com.weaveyourtrip.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaveyourtrip.model.FlightSuggestion;
import com.weaveyourtrip.model.HotelSuggestion;
import com.weaveyourtrip.model.Itinerary;
import com.weaveyourtrip.model.ItineraryDay;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.service.AiService;
import com.weaveyourtrip.service.CurrencyService;
import com.weaveyourtrip.service.ItineraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events endpoint that streams an itinerary day-by-day as the AI
 * produces NDJSON lines.
 *
 * <p>Client-side: the {@code itinerary.html} template opens an
 * {@code EventSource} when in {@code streamingMode}; each {@code day} event
 * carries the rendered HTML for one day card, appended to the page in order.
 * A final {@code done} event triggers a page reload to show booking strip +
 * totals.
 *
 * <p>Server-side: we accumulate the AI's parsed events in thread-safe lists
 * and call {@link ItineraryService#completeFromStream} when the stream ends.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final ItineraryService itineraryService;
    private final AiService aiService;
    private final CurrencyService currencyService;
    private final ObjectMapper objectMapper;
    private final SpringTemplateEngine templateEngine;

    @GetMapping(path = "/itinerary/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable UUID id) {
        Itinerary itinerary = itineraryService.findById(id).orElse(null);
        if (itinerary == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error").data("not_found").build())
                    .concatWith(done());
        }

        // Already-generated itinerary — close the stream immediately so the
        // page reloads into normal render mode
        if (!itineraryService.isPending(itinerary)) {
            return Flux.just(done().blockFirst());
        }

        // Per-stream accumulators — collected as the AI emits NDJSON lines
        List<ItineraryDay> days = new CopyOnWriteArrayList<>();
        List<FlightSuggestion> flights = new CopyOnWriteArrayList<>();
        List<HotelSuggestion> hotels = new CopyOnWriteArrayList<>();

        String localCcy = currencyService.currencyFor(Passport.valueOf(itinerary.getPassport()));
        String currencySymbol = currencyService.symbol(localCcy);

        Flux<ServerSentEvent<String>> events = aiService
                .streamItinerary(itinerary.getInput(), itinerary.getVisa())
                .concatMap(node -> mapNode(node, days, flights, hotels, currencySymbol))
                // Persist the final state on graceful completion
                .doOnComplete(() -> {
                    log.info("Stream complete for {}: {} days, {} flights, {} hotels",
                            id, days.size(), flights.size(), hotels.size());
                    itineraryService.completeFromStream(id, days, flights, hotels);
                })
                .doOnError(err -> log.error("Stream failed for {}: {}", id, err.getMessage(), err))
                .concatWith(done());

        // Keep-alive comment every 15s prevents proxies from closing the stream
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        return Flux.merge(events, heartbeat).takeUntilOther(events.last().materialize());
    }

    /**
     * Map one parsed NDJSON node to zero-or-one SSE events. {@code day} events
     * carry rendered HTML; {@code flight}/{@code hotel} events just accumulate
     * in the server-side lists (revealed only on final reload).
     */
    private Flux<ServerSentEvent<String>> mapNode(JsonNode node,
                                                  List<ItineraryDay> days,
                                                  List<FlightSuggestion> flights,
                                                  List<HotelSuggestion> hotels,
                                                  String currencySymbol) {
        try {
            if (node.has("day")) {
                ItineraryDay day = objectMapper.treeToValue(node.get("day"), ItineraryDay.class);
                days.add(day);
                String html = renderDayCard(day, days.size() - 1, currencySymbol);
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("day").data(html).build());
            }
            if (node.has("flight")) {
                FlightSuggestion flight = objectMapper.treeToValue(
                        node.get("flight"), FlightSuggestion.class);
                flights.add(flight);
            } else if (node.has("hotel")) {
                HotelSuggestion hotel = objectMapper.treeToValue(
                        node.get("hotel"), HotelSuggestion.class);
                hotels.add(hotel);
            }
            // highlights / done — no client event needed (done is appended below)
        } catch (Exception e) {
            log.warn("Failed to map NDJSON node: {}", e.getMessage());
        }
        return Flux.empty();
    }

    /** Render one day card as a single-line HTML string for SSE transport. */
    private String renderDayCard(ItineraryDay day, int dayIndex, String currencySymbol) {
        Context ctx = new Context();
        Map<String, Object> vars = new HashMap<>();
        vars.put("day", day);
        vars.put("dayIndex", dayIndex);
        vars.put("currencySymbol", currencySymbol);
        vars.put("iconForType", ItineraryController.ICON_FOR_TYPE);
        vars.put("activitiesJson", activitiesJson(day));
        ctx.setVariables(vars);

        // SSE protocol requires no raw newlines in the data field — collapse them
        return templateEngine.process("fragments/day-card", ctx)
                .replaceAll("\\s*\\n+\\s*", " ")
                .trim();
    }

    private String activitiesJson(ItineraryDay day) {
        if (day.activities() == null) return "[]";
        List<Map<String, Object>> pins = new ArrayList<>();
        for (var a : day.activities()) {
            pins.add(Map.of(
                    "lat",  a.lat(),
                    "lng",  a.lng(),
                    "name", a.name() != null ? a.name() : "",
                    "time", a.time() != null ? a.time() : ""));
        }
        try {
            return objectMapper.writeValueAsString(pins);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Flux<ServerSentEvent<String>> done() {
        return Flux.just(ServerSentEvent.<String>builder()
                .event("done").data("complete").build());
    }
}
