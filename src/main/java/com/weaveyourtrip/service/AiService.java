package com.weaveyourtrip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weaveyourtrip.model.ItineraryContent;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.model.VisaRequirement;
import com.weaveyourtrip.model.WizardInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wraps Spring AI's {@link ChatClient} for itinerary generation. Provider is
 * configured via {@code application.yml} ({@code spring.ai.google.genai.*});
 * swap to Claude / Ollama / Groq by changing one config block.
 *
 * <p>Day 6 ships synchronous generation — one round-trip, full
 * {@link ItineraryContent} back. NDJSON streaming + SSE land Day 7.
 */
@Service
@Slf4j
public class AiService {

    private static final String SYSTEM_PROMPT = """
            You are WeaveYourTrip, an expert travel planner. Generate a detailed
            day-by-day itinerary plus illustrative flight and hotel suggestions.

            Respond in valid JSON only — no prose, no markdown. Use real place
            names, real airlines, real hotel chains or styles, realistic
            local-currency costs, practical travel times. Include lat/lng
            coordinates for every activity using WGS84 decimal degrees.

            Flight times and prices are illustrative — the user will verify on
            the booking site. Hotel availability is illustrative too.

            Adapt the plan to the traveller profile given. Respect dietary
            preferences across all restaurant suggestions. Stay within the
            stated budget. If a visa is required, ensure the trip duration
            fits within the visa validity window.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Synchronous itinerary generation. Spring AI's {@code BeanOutputConverter}
     * appends a JSON-schema instruction to the prompt and parses the response
     * into {@link ItineraryContent}.
     */
    public ItineraryContent generate(WizardInput input, VisaRequirement visa) {
        String userPrompt = buildUserPrompt(input, visa);
        log.info("Generating itinerary: passport={} destination={} days={}",
                input.getPassport(), input.getDestination(), input.totalDays());
        log.debug("User prompt:\n{}", userPrompt);

        ItineraryContent content = chatClient.prompt()
                .user(userPrompt)
                .call()
                .entity(ItineraryContent.class);

        log.info("AI returned {} days, {} flight suggestions, {} hotel suggestions",
                content.days() == null ? 0 : content.days().size(),
                content.flights() == null ? 0 : content.flights().size(),
                content.hotels() == null ? 0 : content.hotels().size());
        return content;
    }

    /**
     * Streaming itinerary generation — emits one parsed JSON node per logical
     * line of NDJSON. The model is instructed to emit one JSON object per line
     * (highlights, then per-flight, per-hotel, per-day, finally a "done" line).
     *
     * <p>Spring AI returns text chunks via {@code .stream().content()}; we
     * accumulate them in a shared buffer, split on newlines, and emit each
     * complete line as a parsed {@link JsonNode}. Malformed lines are logged
     * and skipped — the stream continues.
     */
    public Flux<JsonNode> streamItinerary(WizardInput input, VisaRequirement visa) {
        String userPrompt = buildStreamingPrompt(input, visa);
        log.info("Streaming itinerary: passport={} destination={} days={}",
                input.getPassport(), input.getDestination(), input.totalDays());

        AtomicReference<StringBuilder> bufferRef = new AtomicReference<>(new StringBuilder());

        return chatClient.prompt()
                .user(userPrompt)
                .stream()
                .content()
                .concatMap(chunk -> Flux.fromIterable(extractCompleteLines(bufferRef.get(), chunk)))
                .concatMap(line -> {
                    JsonNode node = parseNdjsonLine(line);
                    return node != null ? Flux.just(node) : Flux.empty();
                });
    }

    /**
     * Append the new chunk to the buffer, return any newline-terminated lines
     * that are now complete, and leave any partial trailing line in the buffer.
     */
    private List<String> extractCompleteLines(StringBuilder buffer, String chunk) {
        buffer.append(chunk);
        List<String> lines = new ArrayList<>();
        int newlineIdx;
        while ((newlineIdx = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newlineIdx).trim();
            buffer.delete(0, newlineIdx + 1);
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    private JsonNode parseNdjsonLine(String line) {
        // Defensive cleanup: strip code-fence markers if the LLM emits them anyway
        String cleaned = line.replaceAll("^```(?:json|ndjson)?", "")
                             .replaceAll("```$", "")
                             .trim();
        if (cleaned.isEmpty() || cleaned.startsWith("//")) return null;
        try {
            return objectMapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            log.warn("Skipping malformed NDJSON line: {}", cleaned);
            return null;
        }
    }

    private String buildStreamingPrompt(WizardInput input, VisaRequirement visa) {
        return buildUserPrompt(input, visa) + """

                IMPORTANT — STREAMING OUTPUT FORMAT:

                Respond ONLY as NDJSON: one JSON object per line, terminated by a newline (\\n).
                Do NOT wrap output in an outer object, array, or markdown code fence.

                Emit lines in this exact order:
                  1. One line: {"highlights": ["...","..."]}
                  2. Up to 4 lines (only if flights requested): {"flight": {airline, departureAirport, arrivalAirport, departureTime, arrivalTime, stops, durationMinutes, priceLocal, priceEur, cabinClass}}
                  3. Up to 4 lines (only if hotels requested): {"hotel": {name, neighborhood, tier, rating, pricePerNightLocal, totalPriceLocal, amenities}}
                  4. One line per day: {"day": {dayNumber, date, theme, estimatedDailyCostLocal, activities:[{time, type, name, description, location, lat, lng, estimatedCostLocal, durationMinutes, tags, dietaryFlags}]}}
                  5. Final line: {"done": true}

                Each line must be a complete, parseable JSON object. No commentary between lines.
                """;
    }

    /**
     * Generates a consulate-ready cover letter referencing the actual itinerary
     * days. Used in the Visa Checklist PDF (Day 10).
     */
    public String generateCoverLetter(WizardInput input, ItineraryContent content) {
        String prompt = """
                Write a concise visa application cover letter for the trip below.
                Tone: formal, factual, polite. Length: 150–200 words.
                Address it to the consulate. State purpose of travel, duration,
                ties to home country, and confirm intent to return.

                Trip: %s passport → %s, %s to %s (%d days).
                Mode: %s. Group: %d adults, %d children.
                Day themes: %s.

                Return plain text only, no markdown.
                """.formatted(
                input.getPassport(),
                input.getDestination(),
                input.getStartDate(),
                input.getEndDate(),
                input.totalDays(),
                input.getMode(),
                input.getAdults(),
                input.getChildren(),
                content.days() == null ? "" :
                        content.days().stream()
                                .map(d -> "Day " + d.dayNumber() + ": " + d.theme())
                                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b)
        );

        return chatClient.prompt()
                .system("You write formal visa application cover letters in plain text only.")
                .user(prompt)
                .call()
                .content();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Prompt assembly
    // ───────────────────────────────────────────────────────────────────────

    private String buildUserPrompt(WizardInput input, VisaRequirement visa) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("Passport: ").append(input.getPassport()).append("    ");
        sb.append("Trip mode: ").append(input.getMode()).append("\n");
        sb.append("Destination: ").append(input.getDestination()).append("\n");
        sb.append("Dates: ").append(input.getStartDate())
                .append(" to ").append(input.getEndDate())
                .append(" (").append(input.totalDays()).append(" days)\n");
        sb.append("Group: ").append(input.getAdults()).append(" adults");
        if (input.getChildren() > 0) {
            sb.append(", ").append(input.getChildren()).append(" children");
            if (input.getChildAges() != null && !input.getChildAges().isEmpty()) {
                sb.append(" (ages: ").append(input.getChildAges()).append(")");
            }
        }
        sb.append("\n");

        sb.append("Budget: ").append(input.getBudgetTotalLocal())
                .append(" in local currency, total\n");

        if (input.getInterests() != null && !input.getInterests().isEmpty()) {
            sb.append("Interests: ").append(String.join(", ", input.getInterests())).append("\n");
        }
        if (input.getDietary() != null && !input.getDietary().isEmpty()) {
            sb.append("Dietary: ").append(String.join(", ", input.getDietary())).append("\n");
        }
        if (input.getAccommodationTier() != null) {
            sb.append("Accommodation tier: ").append(input.getAccommodationTier()).append("\n");
        }
        if (input.getPace() != null) {
            sb.append("Pace: ").append(input.getPace()).append("\n");
        }
        sb.append("Include flights: ").append(input.isWantFlights()).append("\n");
        sb.append("Include hotels: ").append(input.isWantHotels()).append("\n");

        // Visa context
        if (visa != null && visa.required()) {
            sb.append("\nVisa context: ").append(visa.type())
                    .append(", valid for ").append(visa.validityDays()).append(" days,")
                    .append(" ").append(visa.entries()).append(".\n");
            sb.append("IMPORTANT: trip duration must fit within visa validity.\n");
        } else {
            sb.append("\nVisa context: none required for this corridor.\n");
        }

        // Cultural nudges
        String cultural = culturalContext(input);
        if (!cultural.isEmpty()) {
            sb.append("\nCultural context for this trip:\n").append(cultural);
        }

        sb.append("""

                Generate the itinerary as JSON with this shape:
                {
                  "highlights": ["top experience", ...],
                  "flights": [%s],
                  "hotels": [%s],
                  "days": [
                    {
                      "dayNumber": 1, "date": "yyyy-MM-dd", "theme": "...",
                      "estimatedDailyCostLocal": N,
                      "activities": [
                        {
                          "time": "HH:mm", "type": "sightseeing|food|transport|shopping|nature",
                          "name": "...", "description": "...", "location": "neighborhood",
                          "lat": 0.0, "lng": 0.0,
                          "estimatedCostLocal": N, "durationMinutes": N,
                          "tags": ["..."], "dietaryFlags": ["..."]
                        }
                      ]
                    }
                  ]
                }
                Up to 4 flights, up to 4 hotels. Days array length must equal trip duration.
                """.formatted(
                input.isWantFlights() ? "up to 4 suggestions" : "empty",
                input.isWantHotels() ? "up to 4 suggestions" : "empty"
        ));

        return sb.toString();
    }

    /** Precompute cultural context rules — keeps the prompt deterministic. */
    private String culturalContext(WizardInput input) {
        StringJoiner sj = new StringJoiner("\n");

        if (input.getPassport() == Passport.IN) {
            sj.add("- Indian traveller: prefer hotels with vegetarian breakfast option, "
                    + "family rooms when group > 2, attached private bathroom (not shared).");
        }
        if (input.getDietary() != null) {
            if (input.getDietary().contains("vegetarian")) {
                sj.add("- VEGETARIAN: every meal must have substantial veg options. "
                        + "Flag explicitly veg-friendly restaurants.");
            }
            if (input.getDietary().contains("vegan")) {
                sj.add("- VEGAN: avoid dairy and eggs. Plant-based meals only.");
            }
            if (input.getDietary().contains("halal")) {
                sj.add("- HALAL: flag halal-friendly restaurants. Avoid pork-heavy markets.");
            }
            if (input.getDietary().contains("jain")) {
                sj.add("- JAIN: no onion, no garlic, no root vegetables. Specify Jain prep where possible.");
            }
            if (input.getDietary().contains("no-beef")) {
                sj.add("- NO BEEF: avoid steak houses, mention non-beef alternatives.");
            }
            if (input.getDietary().contains("no-pork")) {
                sj.add("- NO PORK: avoid pork-centric venues, flag pork-free options.");
            }
        }

        // Destination-specific
        if (input.getDestination() != null) {
            String dest = input.getDestination().toLowerCase();
            if (dest.contains("uae") || dest.contains("saudi") || dest.contains("iran")
                    || dest.contains("dubai") || dest.contains("abu dhabi")) {
                sj.add("- Destination has alcohol restrictions — note in cultural section.");
            }
        }

        return sj.toString();
    }
}
