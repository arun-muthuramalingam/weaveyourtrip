package com.weaveyourtrip.service;

import com.weaveyourtrip.model.Itinerary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders {@link Itinerary} aggregates to PDF via Flying Saucer + OpenPDF.
 *
 * <p>Two flavours:
 * <ul>
 *   <li>Itinerary PDF — full day-by-day plan + booking strip + totals</li>
 *   <li>Visa checklist PDF — application document list + AI-generated cover letter</li>
 * </ul>
 *
 * <p>Flying Saucer is XHTML-strict and has no support for flexbox or grid,
 * so the PDF templates ({@code itinerary-pdf.html}, {@code visa-checklist-pdf.html})
 * use table-based layouts only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExportService {

    private final SpringTemplateEngine templateEngine;
    private final CurrencyService currencyService;
    private final AiService aiService;

    /** Render the full itinerary PDF. */
    public byte[] renderItineraryPdf(Itinerary itinerary) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("itinerary", itinerary);
        vars.put("currencySymbol", currencyService.symbol(
                currencyService.currencyFor(
                        com.weaveyourtrip.model.Passport.valueOf(itinerary.getPassport()))));
        return render("itinerary-pdf", vars);
    }

    /** Render the visa checklist PDF, including an AI-generated cover letter. */
    public byte[] renderVisaChecklistPdf(Itinerary itinerary) {
        if (itinerary.getVisa() == null || !itinerary.getVisa().required()) {
            throw new IllegalStateException(
                    "Cannot render visa checklist — no visa requirement on this itinerary");
        }

        // Cache the cover letter on the entity so we don't re-call the AI on every download.
        // (Persistence happens elsewhere — we just compute on the fly here for MVP.)
        String coverLetter = itinerary.getCoverLetter();
        if (coverLetter == null || coverLetter.isBlank()) {
            try {
                // Reconstruct an ItineraryContent shell so AiService can summarise day themes
                com.weaveyourtrip.model.ItineraryContent content =
                        new com.weaveyourtrip.model.ItineraryContent(
                                null, null, null, itinerary.getDays());
                coverLetter = aiService.generateCoverLetter(itinerary.getInput(), content);
                itinerary.setCoverLetter(coverLetter);
            } catch (Exception e) {
                log.warn("Cover letter generation failed: {}", e.getMessage());
                coverLetter = "(Cover letter could not be generated — please write your own.)";
            }
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("itinerary", itinerary);
        vars.put("visa", itinerary.getVisa());
        vars.put("coverLetter", coverLetter);
        return render("visa-checklist-pdf", vars);
    }

    private byte[] render(String templateName, Map<String, Object> vars) {
        Context ctx = new Context();
        ctx.setVariables(vars);
        String html = templateEngine.process(templateName, ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF render failed for template '{}': {}", templateName, e.getMessage(), e);
            throw new RuntimeException("PDF rendering failed", e);
        }
    }
}
