# WeaveYourTrip — Implementation Progress

**Last updated:** 2026-06-07

This file tracks what's actually shipped vs. planned. Each item is marked once
its files are in place; "verified" means the user has run it end-to-end and
confirmed it works.

## Status legend

| Symbol | Meaning |
|---|---|
| ✅ | Implemented + verified end-to-end |
| 🚧 | Implemented, awaiting user verification |
| ⏳ | Planned, not yet implemented |
| ❌ | Blocked, deferred, or descoped |

---

## Week 1 — Foundations

### Day 1 · Spring Boot scaffold
**Status:** 🚧

- [x] Maven project from start.spring.io
- [x] Spring Boot 3.4.4 + Java 21
- [x] Dependencies: Web, WebFlux, JPA, Flyway, Thymeleaf, Validation, Actuator, DevTools, Lombok, Mail
- [x] Spring AI 1.0.0 BOM + `spring-ai-starter-model-google-genai`
- [x] Caffeine + Flying Saucer 9.11.4 (manual `pom.xml` adds)
- [x] Tests consolidated to `spring-boot-starter-test` + `reactor-test`
- [x] Main class renamed `WeaveyourtripApplication` → `WeaveYourTripApplication`

### Day 2 · Database + AI infrastructure
**Status:** 🚧

- [x] `compose.yml` — PostgreSQL 16 with healthcheck
- [x] `application.yml` with `local` + `prod` profiles (replaces `.properties`)
- [x] `V1__initial_schema.sql` — `itinerary` + `visa_correction` with JSONB
- [x] `AiSmokeTest` — calls Gemini, skips if `GEMINI_API_KEY` unset
- [x] Per-IP daily generation cap configured

### Day 3 · Domain + VisaService
**Status:** 🚧

- [x] `Passport` enum (IN, US, GB, EU, OTHER)
- [x] `TripMode` enum (BACKPACKER, COUPLE, FAMILY, GROUP, SENIOR)
- [x] `VisaDocument` + `VisaRequirement` records with nested `ProcessingDays`
- [x] `WizardInput` — Lombok `@Data` DTO with Bean Validation
- [x] `VisaService` — classpath JSON loader + lookup + earliest-departure calc
- [x] `VisaServiceTest` — 9 tests, no Spring context needed
- [x] Visa data files moved into `src/main/resources/visa-data/`

### Day 4 · Wizard steps 1-2 + visa card
**Status:** 🚧

- [x] `HomeController` + `index.html` landing
- [x] `WizardController` steps 1 + 2
- [x] `VisaController` + HTMX `/visa/card?destination=` endpoint
- [x] `WizardSessionConfig` — session-scoped `WizardInput`
- [x] `templates/fragments/head.html` + `wizard-nav.html` + `visa-card.html`
- [x] Prototype CSS ported to `static/css/`

### Day 5 · Wizard steps 3-6 + generate stub
**Status:** 🚧

- [x] Step 3 — dates + group + budget (visa-aware date pre-fill)
- [x] Step 4 — mode picker (5 modes, 3 enabled in v1.0)
- [x] Step 5 — style + dietary + mode-specific blocks
- [x] Step 6 — review + flights/hotels opt-in
- [x] `@ModelAttribute("wizardInput")` form-binding
- [x] `/api/generate` stub + `itinerary-stub.html`

---

## Week 2 — AI

### Day 6 · Real AI generation
**Status:** 🚧

- [x] Records: `Activity`, `ItineraryDay`, `FlightSuggestion`, `HotelSuggestion`, `ItineraryContent`
- [x] `Itinerary` JPA entity with `@JdbcTypeCode(SqlTypes.JSON)` JSONB columns
- [x] `ItineraryRepository`
- [x] `RateLimiter` — in-memory per-IP daily cap
- [x] `BookingUrlService` — Skyscanner + Booking.com URL templating
- [x] `AiService` — Spring AI `ChatClient`, system + user prompt assembly
- [x] `ItineraryService` — orchestrates lookup + AI + enrichment + persist
- [x] `ItineraryController` + `itinerary.html` template
- [x] `WizardController` `/api/generate` wired to real flow
- [x] AI cover-letter generation method exists (not yet wired)

### Day 7 · Flights & Hotels selection
**Status:** 🚧

- [x] `SelectionController` — flight + hotel pickers
- [x] `flights.html` template — AI-suggested list with Top-pick badge
- [x] `hotels.html` template — picsum placeholder images, ratings, amenities
- [x] `ItineraryService.selectFlight()` + `selectHotel()` + `recomputeTotals()`
- [x] Booking strip on itinerary view has Change links + Book deep-links

---

## Week 3 — Polish

### Day 8 · Maps + Currency
**Status:** 🚧

- [x] `CurrencyService` — Frankfurter.app FX rates, cached daily via Caffeine
- [x] Indian number grouping helper (1,80,000 style)
- [x] Multi-currency display in itinerary (local + EUR secondary)
- [x] `GeocodingService` — Nominatim wrapper, 1 req/sec, 30-day cache
- [x] Bounding-box coord validation (no network calls)
- [x] Leaflet 1.9 in head fragment (CSS + JS via CDN)
- [x] `static/js/itinerary-maps.js` — numbered markers + dashed polyline route
- [x] Per-day map embedded in each day card
- [x] Activities serialised to JSON via `ObjectMapper`, passed via `data-activities`

### Day 9 · SSE streaming
**Status:** 🚧

- [x] NDJSON line parser inside `AiService` — buffers chunks, splits on `\n`, parses lines
- [x] `AiService.streamItinerary()` returning `Flux<JsonNode>`
- [x] Streaming prompt — emits `{"highlights"}`, `{"flight"}`, `{"hotel"}`, `{"day"}`, `{"done"}` per line
- [x] `ItineraryService.createPending()` + `completeFromStream()`
- [x] `StreamController` `/itinerary/{id}/stream` SSE endpoint with heartbeat
- [x] `fragments/day-card.html` extracted so server can render single-day HTML for SSE
- [x] Vanilla `EventSource` listener in `itinerary.html` — appends day cards as they arrive
- [x] `window.WYT.initMaps` exposed for re-init after each new day
- [x] Page reloads on `done` event to show booking strip + totals
- [x] `WizardController.generate()` → `createPending()` → redirect immediately

### Day 10 · PDFs
**Status:** 🚧

- [x] `PdfExportService` — Flying Saucer + OpenPDF renderer
- [x] `itinerary-pdf.html` template (table-based, Flying-Saucer-friendly)
- [x] `visa-checklist-pdf.html` template — documents + AI cover letter
- [x] `/itinerary/{id}/pdf` endpoint
- [x] `/itinerary/{id}/visa-checklist.pdf` endpoint
- [x] `AiService.generateCoverLetter()` wired into visa PDF flow
- [x] PDF download buttons in itinerary nav (visible only post-streaming)

### Day 11 · Container + PWA + polish
**Status:** 🚧 (container parts done; PWA and verifier still pending)

- [x] `Dockerfile` — multi-stage build, non-root user, Java 21 JRE runtime
- [x] `.dockerignore` — keeps target/, docs/, prototype/, secrets out of image
- [x] `compose.yml` extended with `app` service (compose profile `app`)
- [x] `application.yml` docker profile — connects to `db:5432` hostname
- [x] `.env.example` template + `.env` added to `.gitignore`
- [x] `README.md` documents both dev mode + full-container mode
- [ ] `manifest.json` — PWA name, icons, theme
- [ ] `sw.js` — service worker with stale-while-revalidate for `/itinerary/*`
- [ ] OSM tile caching in service worker
- [ ] `VisaDataVerifier` daily scheduled job (skeleton exists, needs body)
- [ ] Fly.io deploy (skipped per user — local-only target for now)

---

## Cumulative file count

| Layer | Count |
|---|---|
| Java sources | 23 |
| Java tests | 3 |
| Templates | 12 |
| Migrations | 1 |
| Visa data | 2 |
| Static | 3 |

## Verification commands

```bash
# Day 2 — infrastructure smoke
docker compose up -d
export GEMINI_API_KEY=AIza...
./mvnw test

# Day 3 — visa service in isolation (no DB / AI needed)
./mvnw test -Dtest=VisaServiceTest

# Day 4+ — full app
./mvnw spring-boot:run
# then walk http://localhost:8080
```

## Notes for future sessions

- `/workspace/CLAUDE.md` has the project orientation (brand, decisions, constraints).
- `/workspace/docs/weaveyourtrip-mvp-plan.md` has the full plan + roadmap.
- Visa JSON values are **acid-test placeholders** — verify against official VFS pages before launch.
