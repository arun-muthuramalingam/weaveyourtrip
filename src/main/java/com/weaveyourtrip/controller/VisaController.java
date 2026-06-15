package com.weaveyourtrip.controller;

import com.weaveyourtrip.model.VisaRequirement;
import com.weaveyourtrip.model.WizardInput;
import com.weaveyourtrip.service.VisaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Serves the HTMX visa-card fragment. The wizard step-2 input field fires
 * {@code GET /visa/card?destination=...} on change; this endpoint returns one
 * of three fragment variants ({@code empty}, {@code unsupported},
 * {@code required}, {@code notRequired}) which HTMX swaps into the page.
 *
 * <p>The passport comes from session-scoped {@link WizardInput} — the URL only
 * carries the destination, keeping HTMX wiring simple.
 */
@Controller
@RequiredArgsConstructor
public class VisaController {

    private final WizardInput wizardInput;
    private final VisaService visaService;

    @GetMapping("/visa/card")
    public String visaCard(@RequestParam(required = false) String destination, Model model) {

        // Empty input → return empty fragment so the slot stays blank
        if (destination == null || destination.isBlank() || wizardInput.getPassport() == null) {
            return "fragments/visa-card :: empty";
        }

        Optional<VisaRequirement> req = visaService.lookup(wizardInput.getPassport(), destination);

        if (req.isEmpty()) {
            model.addAttribute("destination", destination);
            return "fragments/visa-card :: unsupported";
        }

        VisaRequirement r = req.get();
        model.addAttribute("requirement", r);

        if (!r.required()) {
            return "fragments/visa-card :: notRequired";
        }

        // Required-visa variant — add computed earliest-viable-departure + total fee
        LocalDate earliest = visaService.earliestViableDeparture(r, LocalDate.now());
        int totalFee = (r.feeEur() != null ? r.feeEur() : 0)
                     + (r.serviceFeeEur() != null ? r.serviceFeeEur() : 0);

        model.addAttribute("earliestDeparture", earliest);
        model.addAttribute("totalFee", totalFee);

        return "fragments/visa-card :: required";
    }
}
