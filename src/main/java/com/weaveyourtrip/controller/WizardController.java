package com.weaveyourtrip.controller;

import com.weaveyourtrip.model.Itinerary;
import com.weaveyourtrip.model.Passport;
import com.weaveyourtrip.model.TripMode;
import com.weaveyourtrip.model.VisaRequirement;
import com.weaveyourtrip.model.WizardInput;
import com.weaveyourtrip.service.ItineraryService;
import com.weaveyourtrip.service.RateLimiter;
import com.weaveyourtrip.service.VisaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Drives the 6-step wizard. Form posts mutate the session-scoped
 * {@link WizardInput}; GETs render the current step's view.
 *
 * <p>Pattern: each step has a {@code GET /plan/...} and {@code POST /plan/...}.
 * POSTs use {@code @ModelAttribute("wizardInput")} so Spring binds form fields
 * directly onto the session bean. Redirects then take the user to the next step.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WizardController {

    private final WizardInput wizardInput;
    private final VisaService visaService;
    private final ItineraryService itineraryService;
    private final RateLimiter rateLimiter;

    /**
     * Exposes the session-scoped bean to every template + binds POST form fields
     * onto it. Triggered once per request before handlers run.
     */
    @ModelAttribute("wizardInput")
    public WizardInput wizardInputModel() {
        return wizardInput;
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 1 — passport picker
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan")
    public String step1(@RequestParam(required = false) Passport passport, Model model) {
        if (passport != null) {
            wizardInput.setPassport(passport);
            return "redirect:/plan/destination";
        }
        model.addAttribute("passports", Passport.values());
        return "wizard/step1-passport";
    }

    @PostMapping("/plan/passport")
    public String submitPassport(@RequestParam Passport passport) {
        wizardInput.setPassport(passport);
        return "redirect:/plan/destination";
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 2 — destination + visa card (lazy-loaded via VisaController)
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan/destination")
    public String step2(Model model) {
        if (wizardInput.getPassport() == null) return "redirect:/plan";
        model.addAttribute("passportFlag", wizardInput.getPassport().getFlag());
        model.addAttribute("passportName", wizardInput.getPassport().getDisplayName());
        model.addAttribute("quickPicks", quickPicks());
        return "wizard/step2-destination";
    }

    @PostMapping("/plan/destination")
    public String submitDestination(@RequestParam String destination) {
        wizardInput.setDestination(destination);
        return "redirect:/plan/dates";
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 3 — dates + group + budget
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan/dates")
    public String step3(Model model) {
        if (wizardInput.getDestination() == null) return "redirect:/plan/destination";

        // Pre-fill earliest viable departure if visa required + dates not yet set
        VisaRequirement req = visaService.lookup(wizardInput.getPassport(), wizardInput.getDestination()).orElse(null);
        LocalDate earliest = visaService.earliestViableDeparture(req, LocalDate.now());

        if (wizardInput.getStartDate() == null) {
            wizardInput.setStartDate(earliest);
            wizardInput.setEndDate(earliest.plusDays(10));
        }

        model.addAttribute("earliestDeparture", req != null && req.required() ? earliest : null);
        model.addAttribute("minStartDate", earliest);
        return "wizard/step3-dates-group";
    }

    @PostMapping("/plan/dates")
    public String submitDates(@ModelAttribute("wizardInput") WizardInput bound) {
        // Spring already bound the form fields onto the session bean. Just advance.
        return "redirect:/plan/mode";
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 4 — trip mode picker
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan/mode")
    public String step4(Model model) {
        if (wizardInput.getStartDate() == null) return "redirect:/plan/dates";
        model.addAttribute("modes", TripMode.values());
        model.addAttribute("enabledModes", List.of("BACKPACKER", "COUPLE", "FAMILY"));
        model.addAttribute("modeDescriptions", Map.of(
                "BACKPACKER", "Budget-conscious, flexible, max experience per euro.",
                "COUPLE",     "Romantic trips, anniversaries, honeymoons.",
                "FAMILY",     "Logistics-heavy trips with kids.",
                "GROUP",      "Coordinate opinions, split expenses.",
                "SENIOR",     "Comfort-first, accessibility, peace of mind."
        ));
        return "wizard/step4-mode";
    }

    @PostMapping("/plan/mode")
    public String submitMode(@RequestParam TripMode mode) {
        wizardInput.setMode(mode);
        return "redirect:/plan/style";
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 5 — style + dietary + mode-specific
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan/style")
    public String step5(Model model) {
        if (wizardInput.getMode() == null) return "redirect:/plan/mode";
        model.addAttribute("interestOptions", interestOptions());
        model.addAttribute("dietaryOptions", dietaryOptions());
        return "wizard/step5-style";
    }

    @PostMapping("/plan/style")
    public String submitStyle(@ModelAttribute("wizardInput") WizardInput bound) {
        return "redirect:/plan/review";
    }

    // ───────────────────────────────────────────────────────────────────────
    // STEP 6 — review + generate
    // ───────────────────────────────────────────────────────────────────────

    @GetMapping("/plan/review")
    public String step6(Model model) {
        if (wizardInput.getMode() == null) return "redirect:/plan/style";
        VisaRequirement req = visaService.lookup(wizardInput.getPassport(), wizardInput.getDestination()).orElse(null);
        model.addAttribute("requirement", req);
        return "wizard/step6-review";
    }

    // ───────────────────────────────────────────────────────────────────────
    // GENERATE — synchronous AI call, persist, redirect to /itinerary/{id}
    //           (NDJSON streaming via SSE lands Day 7+)
    // ───────────────────────────────────────────────────────────────────────

    @PostMapping("/api/generate")
    public String generate(@ModelAttribute("wizardInput") WizardInput bound,
                           HttpServletRequest request) {

        if (bound.getPassport() == null || bound.getDestination() == null
                || bound.getStartDate() == null || bound.getMode() == null) {
            return "redirect:/plan";
        }

        String ip = clientIp(request);
        if (!rateLimiter.tryAcquire(ip)) {
            log.warn("Rate-limited generation request from ip={}", ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily generation limit reached. Try again tomorrow.");
        }

        // Day 9: create a placeholder Itinerary immediately so we can redirect
        // the user to /itinerary/{id} right away. The page then opens an SSE
        // connection that drives AI generation + day-by-day rendering.
        Itinerary skeleton = itineraryService.createPending(bound);
        return "redirect:/itinerary/" + skeleton.getId();
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ───────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────

    private List<String> quickPicks() {
        return List.of(
                "🇫🇷 France", "🇮🇹 Italy", "🇩🇪 Germany",
                "🇪🇸 Spain", "🇳🇱 Netherlands", "🇬🇧 United Kingdom"
        );
    }

    private List<Option> interestOptions() {
        return List.of(
                new Option("local-food",      "🍜 Local Food"),
                new Option("museums",         "🏛️ Museums"),
                new Option("nightlife",       "🎉 Nightlife"),
                new Option("hiking",          "🥾 Hiking"),
                new Option("architecture",    "🏯 Architecture"),
                new Option("markets",         "🛍️ Local Markets"),
                new Option("religious-sites", "⛪ Religious Sites"),
                new Option("photography",     "📸 Photography Spots"),
                new Option("art",             "🎨 Art Galleries"),
                new Option("nature",          "🌿 Nature & Parks")
        );
    }

    private List<Option> dietaryOptions() {
        return List.of(
                new Option("vegetarian", "🥬 Vegetarian"),
                new Option("vegan",      "🌱 Vegan"),
                new Option("halal",      "☪️ Halal"),
                new Option("jain",       "🕉️ Jain"),
                new Option("no-beef",    "🐄 No beef"),
                new Option("no-pork",    "🐖 No pork")
        );
    }

    /** Display label + form value for chip rendering. */
    public record Option(String value, String label) {
    }
}
