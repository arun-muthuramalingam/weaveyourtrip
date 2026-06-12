# WeaveYourTrip — Implementation Plan v2

**Last updated:** 2026-06-07 · **Status:** Week 1 Day 1 complete — scaffolding done · **Owner:** Arun Muthuramalingam

---

## 0. Brand Kit (locked)

| | |
|---|---|
| Name | **WeaveYourTrip** (renamed from TripForge after honest naming pass — `weyve.com` was €6,000 premium) |
| Domain | `weaveyourtrip.com` (Namecheap, €10/year) |
| Tagline | **Travel that starts where you do.** |
| USP line | The AI travel planner built around your passport. |
| Action verb in copy | "weave" (not "forge") — *"Weave my trip →"*, *"Your trip. Woven perfectly."* |
| Java package | `com.weaveyourtrip` |
| Maven coords | `com.weaveyourtrip:weaveyourtrip:0.0.1-SNAPSHOT` |
| Main class | `WeaveYourTripApplication` |

---

## 1. What MVP v1.0 Ships

A web app that asks for your passport before your destination, surfaces visa requirements before you commit to dates, then generates a personalised day-by-day itinerary tailored to your travel mode. AI-suggested flights/hotels redirect to Skyscanner/Booking; full itinerary and visa-checklist PDFs download. Works offline once loaded.

### In scope (MVP v1.0 — 6-week build)

| Layer | Scope |
|---|---|
| Passports | India (🇮🇳) only — full visa-aware flow |
| Visa corridors | 5 hand-curated: IN→Schengen, IN→GB, IN→US, IN→AE, IN→TH |
| Traveller modes | 3: **Solo Explorer, Couple Getaway, Family Adventure** |
| Itinerary | AI-generated day-by-day with streaming, max 14 days |
| Maps | Leaflet + OSM, one per day, markers + day route polyline |
| Flights/Hotels | Level 2 stubs (AI-suggested + Skyscanner/Booking deep-link) |
| Currency | INR primary, EUR secondary |
| PDFs | Itinerary PDF + Visa Checklist PDF (with AI-generated cover letter) |
| Offline | PWA — service worker caches itinerary HTML + PDFs |
| Storage | In-memory `ConcurrentHashMap` |
| Affiliate | Placeholder links — no real affiliate IDs yet |

### Explicitly NOT in MVP v1.0

- User accounts / auth (anonymous URLs only)
- Soft-passport flows (US/UK/EU) — wizard supports them but visa data is IN-only
- Group of Friends + Senior Explorer modes — deferred to v1.1
- Real flight/hotel inventory APIs (Travelpayouts, Skyscanner Partners, Booking Connect)
- Visa appointment slot tracking
- Chat refinement of itineraries
- Live weather / flight delay / visa rule alerts
- Mobile native apps
- Real affiliate IDs on URLs (added once accounts approved)
- PDF maps (text-only PDFs in v1.0)

### Roadmap after v1.0

- **v1.1 (week 7–10):** add Couple + Group + Senior modes, 10 more visa corridors, soft-passport (US/GB/EU) data, real affiliate IDs once approved
- **v1.2 (week 11–12):** PostgreSQL migration, user accounts, trip history
- **v2.0 (months 4–6):** Real flight/hotel inventory (Travelpayouts), insurance affiliate, visa appointment monitor (premium)

---

## 2. Tech Stack

| Layer | Choice | Pinned Version | Notes |
|---|---|---|---|
| Backend | Spring Boot | **3.4.4** | Downgraded from 4.0.6 for Spring AI 1.0 compatibility |
| JDK | Java | **21** | Virtual threads for AI streaming |
| Templates | Thymeleaf | (Boot-managed) | Server-side rendering |
| Interactivity | HTMX | 2.x (CDN) | Wizard steps + SSE — no build step |
| AI | Spring AI | **1.0.0** | Provider-agnostic via `ChatClient` |
| AI default | Gemini 2.5 Flash via `spring-ai-starter-model-google-genai` | (BOM-managed) | Free tier (15 RPM, 1M TPM, 1.5K RPD) |
| AI alternates | Claude Sonnet 4.6, Ollama (local), Groq (Llama) | — | Swap via `application.yml` |
| Maps (web) | Leaflet | 1.9.4 (CDN) | + OpenStreetMap tiles, no API key |
| Geocoding | Nominatim (OpenStreetMap) | — | Free, 1 req/sec — server-side cache |
| Currency FX | Frankfurter.app | — | Free, no key, ECB rates |
| PDF | Flying Saucer + OpenPDF | **9.11.4** | Server-side XHTML → PDF |
| Cache | Caffeine | (Boot-managed) | In-memory for geocoding + FX |
| DB | PostgreSQL | 16 (local via Docker) | Production-grade from day one |
| Persistence | Spring Data JPA + Hibernate | (Boot-managed) | JSONB for itinerary content |
| Migrations | Flyway | (Boot-managed) | + `flyway-database-postgresql` |
| Bookings | Skyscanner / Booking.com deep-link URLs | — | Built from `WizardInput` |
| Offline | PWA — `manifest.json` + service worker | — | Caches `/itinerary/*` |
| Validation | Jakarta Bean Validation | (Boot-managed) | On `WizardInput` |
| Build | Maven Wrapper | — | `./mvnw spring-boot:run` |
| Deploy | Fly.io (Mumbai region) | — | Latency target for Indian audience |

HTMX via CDN — no npm, no build toolchain.

---

## 3. API Integrations

### MVP v1.0 — active integrations

| API | Purpose | Free? | Approval lead time | Notes |
|---|---|---|---|---|
| **Gemini API** | AI itinerary generation (default) | Free tier (15 RPM, 1M TPM, 1.5K RPD) | Same-day | Sufficient for early MVP |
| **Anthropic Claude API** | AI generation (alternate, higher quality) | Paid (~$0.06–0.15/plan) | Same-day | Use for production / power users |
| **Frankfurter.app** | Currency FX rates (INR ↔ EUR ↔ GBP) | Free | None | ECB rates, daily refresh, no key |
| **OpenStreetMap tiles** | Map display via Leaflet | Free (usage policy applies) | None | Cache aggressively in service worker |
| **Nominatim** | Geocode AI-generated activity names → lat/lng | Free | None | **1 req/sec hard limit** — cache results, batch carefully |
| **Skyscanner web URLs** | Flight booking deep-link redirect | No API | None | Plain URL templating from wizard inputs |
| **Booking.com search URLs** | Hotel booking deep-link redirect | No API | None | Plain URL templating |
| **Ollama (optional, local)** | On-device AI for power users | Free | None | Spring AI handles it identically |

### Deferred — integrate after MVP

| API | Purpose | Why deferred |
|---|---|---|
| **Travelpayouts** | Unified flight + hotel affiliate (Booking, Aviasales, Hotels.com…) | Approval threshold; single token covers many providers |
| **Stay22** | Accommodation affiliate alternative | Approval; lighter onboarding than Booking Connect |
| **Skyscanner Partners API** | Real flight prices + availability | Months-long approval, traffic thresholds |
| **Booking.com Connect** | Real hotel inventory | Similar approval barrier |
| **Amadeus Self-Service** | Real flight inventory (2000 free calls/month) | Adds error handling + caching layer |
| **Atlys / OneVasco affiliate** | Visa filing service referral | Affiliate program approval |
| **Bajaj Allianz International** | Travel insurance affiliate (mandatory for Schengen) | Affiliate approval |
| **Airalo / Holafly** | eSIM affiliate | Future feature, not v1.0 |
| **OpenWeatherMap / Open-Meteo** | Weather alerts post-departure | Phase 5+ live-updates feature |
| **VFS Global / consulate scrapes** | Visa appointment slot availability | Premium feature, week 11+ |
| **Mapbox Static Images** | Static map PNG for PDF | Adds cost; v1.0 PDFs stay text-only |
| **Google Places** | Richer activity metadata + photos | Cost; current prototype uses picsum |
| **Unsplash / Pexels** | Activity photos | Replaces picsum placeholders post-MVP |
| **Sherpa / VisaHQ APIs** | Live visa rule changes | Hand-curated covers MVP; revisit at scale |

---

## 4. Project Structure

```
weaveyourtrip/
├── pom.xml
└── src/main/
    ├── java/com/weaveyourtrip/
    │   ├── WeaveYourTripApplication.java
    │   ├── controller/
    │   │   ├── HomeController.java            — landing
    │   │   ├── WizardController.java          — 6 steps (HTMX fragments)
    │   │   ├── VisaController.java            — visa card endpoint + error report
    │   │   ├── ItineraryController.java       — view, SSE, PDFs
    │   │   ├── SelectionController.java       — flight/hotel selection
    │   │   └── MapController.java             — geocoding API for client
    │   ├── service/
    │   │   ├── AiService.java                 — Spring AI ChatClient wrapper
    │   │   ├── VisaService.java               — corridor lookup + deadline calc
    │   │   ├── ItineraryService.java          — business logic + storage
    │   │   ├── BookingUrlService.java         — Skyscanner / Booking URL builders
    │   │   ├── CurrencyService.java           — Frankfurter.app FX cache
    │   │   ├── GeocodingService.java          — Nominatim wrapper + cache
    │   │   └── PdfExportService.java          — Flying Saucer renderer
    │   ├── job/
    │   │   └── VisaDataVerifier.java          — daily diff of source URLs
    │   └── model/
    │       ├── Passport.java                  — enum (IN, US, GB, EU, OTHER)
    │       ├── TripMode.java                  — enum (BACKPACKER, COUPLE, FAMILY, GROUP, SENIOR)
    │       ├── WizardInput.java               — validated DTO
    │       ├── VisaRequirement.java
    │       ├── VisaDocument.java
    │       ├── Itinerary.java
    │       ├── ItineraryDay.java
    │       ├── Activity.java                  — incl. lat/lng
    │       ├── DayMap.java                    — coordinates + route polyline
    │       ├── FlightSuggestion.java
    │       └── HotelSuggestion.java
    └── resources/
        ├── templates/
        │   ├── layout.html
        │   ├── index.html
        │   ├── wizard/
        │   │   ├── step1-passport.html
        │   │   ├── step2-destination.html
        │   │   ├── step3-dates-group.html
        │   │   ├── step4-mode.html
        │   │   ├── step5-style.html
        │   │   └── step6-review.html
        │   ├── visa-card.html
        │   ├── itinerary.html                 — Leaflet maps render here
        │   ├── itinerary-pdf.html             — print-optimised, no maps
        │   ├── visa-checklist-pdf.html        — documents + AI cover letter
        │   ├── flights.html
        │   └── hotels.html
        ├── visa-data/                          — hand-curated corridor JSON
        │   ├── IN-SCHENGEN.json
        │   ├── IN-GB.json
        │   ├── IN-US.json
        │   ├── IN-AE.json
        │   └── IN-TH.json
        ├── static/
        │   ├── css/style.css
        │   ├── js/
        │   │   └── itinerary-maps.js          — Leaflet init for day cards
        │   ├── manifest.json                  — PWA
        │   └── sw.js                          — Service worker
        └── application.properties
```

---

## 5. Pages & Routes

| Route | Method | Purpose |
|---|---|---|
| `/` | GET | Landing — passport-first hook |
| `/plan` | GET | Wizard shell + step 1 |
| `/wizard/step/{n}` | POST | Next step fragment (HTMX swap) |
| `/visa/{passport}/{destination}` | GET | Visa card HTML fragment |
| `/visa/{passport}/{destination}/report-error` | POST | Crowdsourced correction |
| `/api/generate` | POST | Kicks off async AI generation, returns `{id}` |
| `/itinerary/{id}` | GET | Itinerary view — SSE + maps + booking strip |
| `/itinerary/{id}/stream` | GET | SSE — pushes day JSON as days complete |
| `/itinerary/{id}/flights` | GET | Flight selection (AI-suggested list) |
| `/itinerary/{id}/hotels` | GET | Hotel selection (AI-suggested list) |
| `/itinerary/{id}/select-flight` | POST | Store choice |
| `/itinerary/{id}/select-hotel` | POST | Store choice |
| `/itinerary/{id}/pdf` | GET | Itinerary PDF |
| `/itinerary/{id}/visa-checklist.pdf` | GET | Visa documents + cover letter PDF |
| `/api/geocode?q={query}` | GET | Server-side Nominatim proxy (cached) |

The "Book" buttons on flights/hotels are plain `<a target="_blank">` — no server route.

---

## 6. Wizard Flow (6 steps)

**Passport-first + mode-aware. Visa step does real work for hard passports, collapses to "✓ no visa needed" for soft passports. Cultural context (currency, dietary defaults) applies regardless.**

### Step 1 — Passport
Single-click country card grid. Pre-selectable via `?passport=IN` URL param.

### Step 2 — Destination + Visa Card
Text input with destination quick-picks. On `change` (debounced 400ms), HTMX fires `GET /visa/{passport}/{destination}` and swaps in the visa card. **Wow moment for hard-passport users.** For soft-passport users, the card collapses to a friendly "✓ No visa needed for 90 days" badge.

### Step 3 — Dates + Group + Budget
Date picker pre-fills with **earliest viable departure** if visa required (today + processing + 7-day buffer). Adults / Children steppers. Budget slider in passport's local currency with secondary EUR conversion.

### Step 4 — Trip Style (Mode picker)
Five mode cards: 🎒 Solo Explorer · 💑 Couple Getaway · 👨‍👩‍👧‍👦 Family Adventure · 👥 Group of Friends · 🌿 Senior Explorer (+ 🌍 Digital Nomad coming soon). Card click auto-advances to step 5.

**v1.0 ships with Solo, Couple, Family only.** Group + Senior remain visible in UI but show "Coming v1.1" badge.

### Step 5 — Style + Dietary + Mode-Specific
Universal: Interests chips, Dietary preferences chips (Veg/Vegan/Halal/Jain/No beef/No pork).

Mode-specific fields rendered at top, varying by chosen mode:
- **Solo Explorer:** Accommodation tier (Hostel/Guesthouse/Budget Hotel)
- **Couple Getaway:** Romantic priorities chips + Boutique tier
- **Family Adventure:** Children ages + Stroller toggle + Medical proximity toggle + Family priority chips
- **Group of Friends:** Group activities focus + Expense splitting toggle *(v1.1)*
- **Senior Explorer:** Mobility radio + Direct flights toggle + Elevator/medical toggles *(v1.1)*

### Step 6 — Review + Generate
Summary card (passport, destination, visa context, dates, mode, dietary, accommodation) + service opt-in (Flights/Hotels) + "Weave my trip →" button.

POSTs to `/api/generate` → redirects to `/itinerary/{id}`.

---

## 7. Visa Intelligence

### Corridor JSON format (`resources/visa-data/IN-SCHENGEN.json`)

```json
{
  "passport": "IN",
  "destination": "SCHENGEN",
  "required": true,
  "type": "Short-stay Schengen Visa (Type C)",
  "processingDays": { "min": 10, "typical": 15, "max": 30 },
  "feeEur": 80,
  "serviceFeeEur": 31,
  "validityDays": 90,
  "entries": "multiple-allowed",
  "applyCenters": ["VFS Mumbai", "VFS Delhi", "..."],
  "appointmentAvailability": "tight",
  "requiredDocuments": [ { "id":"passport", "name":"Original passport", "notes":"..." }, ... ],
  "commonRejectionReasons": [ "Insufficient bank balance proof", ... ],
  "officialSourceUrl": "https://www.vfsglobal.com/...",
  "lastVerified": "2026-06-06"
}
```

### `VisaService` API

```java
VisaRequirement lookup(Passport p, String destination);
LocalDate earliestViableDeparture(VisaRequirement v, LocalDate today, int bufferDays);
List<VisaDocument> checklist(VisaRequirement v, WizardInput input);
String generateCoverLetter(Itinerary i);     // delegates to AiService
```

### Soft-passport corridors (visa not required)

Same JSON structure, `"required": false`, with `maxStayDays`, `stayWindow`, `futureNote` (ETIAS / ETA upcoming). Hand-curate ~10 of these in v1.1 for US/GB/EU → common destinations.

### Data quality

- **Hand-curate top 5 corridors for v1.0:** IN-SCHENGEN, IN-GB, IN-US, IN-AE, IN-TH
- **`VisaDataVerifier`** Spring `@Scheduled` job daily 03:00 UTC — fetches `officialSourceUrl`, hashes content, emails on diff
- **Crowdsourced corrections** via `POST /visa/.../report-error` — manual review weekly
- **Mandatory disclaimer** on every visa card + PDF — `lastVerified` date prominent

---

## 8. Cultural Context

The system prompt adapts to passport + dietary inputs. Examples:

```
Traveler holds {passport} passport.
Dietary preferences: {dietary list}.
Display all costs in {local_currency} alongside EUR.
Trip mode: {mode}.

If passport=IN: prefer hotels with vegetarian breakfast option,
family rooms when group >2, attached private bathroom.

If dietary includes 'halal': flag halal-friendly restaurants explicitly,
avoid pork-heavy market recommendations.

If dietary includes 'jain': no onion / no garlic restaurants, mention Jain-specific
prep where possible.

If destination is UAE/Saudi/Iran: note alcohol restrictions in cultural notes.

If passport=IN and destination=Schengen: ensure trip duration <= 90 days,
recommend applying via consulate of country with most stay (Schengen rule).

Mode adjustments:
- BACKPACKER: budget-first, hostels OK, street food, public transit
- COUPLE: romantic restaurants, sunset views, boutique hotels
- FAMILY: kid-friendly, nap buffer slots, stroller-accessible, family rooms
```

---

## 9. AI Integration

### Provider strategy

Spring AI's `ChatClient` is provider-agnostic. Active provider set in `application.properties`.

| Provider | When | Cost |
|---|---|---|
| **Gemini 2.5 Flash** | Default for MVP/dev | Free tier |
| **Claude Sonnet 4.6** | Production / quality bar | ~$0.06–0.15/plan |
| **Ollama (local)** | Power users, offline dev | Free |
| **Groq (Llama 3.1)** | Alternative low-latency free | Free with rate limits |

### `AiService`

```java
@Service
public class AiService {
    private final ChatClient chatClient;

    public AiService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem(SYSTEM_PROMPT)
            .build();
    }

    public Flux<String> streamItinerary(WizardInput input) {
        return chatClient.prompt()
            .user(buildUserPrompt(input))
            .stream()
            .content();
    }

    public String generateCoverLetter(Itinerary i) { /* one-shot, not streamed */ }
}
```

### Prompt design

**System prompt** (cached on supported providers):
> You are WeaveYourTrip, an expert travel planner. Generate a detailed day-by-day itinerary plus illustrative flight and hotel suggestions. Respond in valid JSON only — no prose. Use real place names, real airlines, real hotel chains/styles, realistic local-currency costs, practical travel times. Include lat/lng coordinates for every activity using WGS84 decimal degrees. Adapt to the traveller profile given. Flight times and prices are illustrative — users will verify on the booking site.

**User prompt:**
```
Passport: {passport}                Mode: {mode}
Origin: {origin} → Destination: {destination}
Dates: {startDate} to {endDate} ({N} days)
Group: {adults} adults, {children} children {ages: [...]}
Budget: {currency}{budget} total
Interests: {interests}
Dietary: {dietary}
Accommodation tier: {tier}    Pace: {pace}
Include flights: {true|false}    Include hotels: {true|false}
Visa context: {visa requirements or "none — soft passport"}
[mode-specific context block]

Return JSON:
{
  "highlights": [...],
  "flights": [ {airline, departureAirport, arrivalAirport, departureTime, arrivalTime, stops, durationMinutes, priceLocal, priceEur, cabinClass}, ... ],
  "hotels":  [ {name, neighborhood, tier, rating, pricePerNightLocal, totalPriceLocal, amenities}, ... ],
  "days": [
    {
      "dayNumber": 1,
      "date": "2026-07-15",
      "theme": "...",
      "estimatedDailyCostLocal": N,
      "activities": [
        { "time":"09:00", "type":"sightseeing", "name":"Notre-Dame Cathedral",
          "description":"...", "location":"4th Arr",
          "lat":48.8530, "lng":2.3499,
          "estimatedCostLocal":0, "durationMinutes":90,
          "tags":["religious","unesco"] },
        ...
      ]
    }
  ]
}
```

Server post-processing:
1. Validates JSON schema
2. Geocodes each activity's `name + location` via Nominatim **only if** AI-provided coords look suspicious (outside destination bounding box)
3. Builds `bookingSearchUrl` on flights/hotels via `BookingUrlService`
4. Assigns local UUID per flight/hotel for selection state

### Streaming via NDJSON

To make day-by-day progressive rendering tractable, ask the model to emit **one JSON object per line** (NDJSON) for `days[]`. Parser reads line-by-line, pushes each day as an SSE event. Avoids fragile mid-JSON parsing.

```
{"highlights":[...]}
{"flights":[...]}
{"hotels":[...]}
{"day":{"dayNumber":1,"date":"...","activities":[...]}}
{"day":{"dayNumber":2,...}}
...
{"done":true,"totalEstimatedCostLocal":N}
```

---

## 10. Maps Integration (NEW)

Each day card in the itinerary view embeds an interactive Leaflet map showing all activities for that day as numbered markers, with a polyline tracing the day's route.

### Frontend (Leaflet)

```html
<!-- In layout.html <head> -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

<!-- In itinerary.html, inside each day card -->
<div class="day-map" id="day-1-map" style="height:280px; border-radius:12px;"
     data-activities='[{"lat":48.853,"lng":2.350,"name":"Notre-Dame","time":"09:00"}, ...]'>
</div>
```

```js
// static/js/itinerary-maps.js
document.querySelectorAll('.day-map').forEach(el => {
  const activities = JSON.parse(el.dataset.activities);
  if (!activities.length) return;

  const map = L.map(el.id).setView([activities[0].lat, activities[0].lng], 13);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
    maxZoom: 19
  }).addTo(map);

  // Numbered markers
  activities.forEach((a, i) => {
    L.marker([a.lat, a.lng], {
      icon: L.divIcon({
        className: 'day-marker',
        html: `<div class="marker-num">${i+1}</div>`,
        iconSize: [32, 32]
      })
    }).bindPopup(`<strong>${a.time}</strong> ${a.name}`).addTo(map);
  });

  // Day route polyline
  if (activities.length > 1) {
    L.polyline(activities.map(a => [a.lat, a.lng]), {
      color: '#c94f2c', weight: 3, opacity: 0.7, dashArray: '6,8'
    }).addTo(map);
  }

  // Fit bounds to all markers
  map.fitBounds(L.latLngBounds(activities.map(a => [a.lat, a.lng])).pad(0.15));
});
```

### Backend: Coordinate handling

The AI returns lat/lng with each activity. Two safety layers:

1. **Validate bounding box:** Each destination has a known bounding box (Schengen, Paris, Tokyo, etc.). Activities outside it fall through to step 2.
2. **Geocode via Nominatim** as fallback. `GeocodingService` wraps Nominatim with:
   - **Caffeine in-memory cache** keyed on `(name, neighborhood)` — survives within session
   - **Rate-limit guard** — 1 req/sec maximum (Nominatim usage policy)
   - **Batch async** — geocode the full itinerary's flagged activities in parallel via virtual threads, respecting limit
   - **Fail-soft** — if geocoding fails, fall back to destination centroid + small offset so map still renders

### Service worker caching

Service worker caches OSM tiles via stale-while-revalidate strategy. After one online view, maps work fully offline through the PWA — critical for the "open itinerary at the airport" use case.

### NOT in v1.0

- **Static maps in PDF** — Leaflet can't render server-side, and Mapbox Static API adds per-image cost. PDFs stay text-only in v1.0. Day maps are a v1.1 enhancement.
- **Routing / walking directions** — markers + straight-line polyline only. Real walking paths (OSRM / Google Directions) deferred.
- **Restaurant booking pins / hotel pin on map** — only activities. Hotel + flight context lives in the booking strip above the days.

---

## 11. Booking URL Builders

`BookingUrlService` constructs deep-link search URLs from `WizardInput`. No API key, no affiliate code in v1.0 — pure URL templating.

### Skyscanner (flights)

```
https://www.skyscanner.net/transport/flights/{from}/{to}/{depart YYMMDD}/{return YYMMDD}/
  ?adults={adults}&children={children}&cabinclass={cabinClass}
```

Example: `.../flights/bom/cdg/260715/260725/?adults=2&cabinclass=economy`

### Booking.com (hotels)

```
https://www.booking.com/searchresults.html
  ?ss={destination URL-encoded}
  &checkin={YYYY-MM-DD}&checkout={YYYY-MM-DD}
  &group_adults={adults}&group_children={children}
  &no_rooms=1
```

### Affiliate parameters (v1.1)

Skyscanner `associateid=`, Booking `aid=`. Added once accounts approved — likely via **Travelpayouts** as unified entry point.

---

## 12. Streaming (HTMX SSE)

```html
<div hx-ext="sse" sse-connect="/itinerary/{id}/stream"
     sse-swap="day" hx-swap="beforeend"
     id="days-container">
</div>
```

`AiService` streams NDJSON from the model. Server parses each completed line and emits an SSE event. Days arrive progressively — no blank loading screen.

```java
@GetMapping(path = "/itinerary/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> stream(@PathVariable UUID id) {
    return itineraryService.streamItinerary(id)
        .map(day -> ServerSentEvent.builder(renderDay(day))
            .event("day")
            .build());
}
```

Map for each day initializes after the day's HTML lands in the DOM (HTMX `htmx:afterSwap` event listener).

---

## 13. PWA & Offline

- **`manifest.json`** in `static/` — name, icons, theme colour (ink/sand), `display: standalone`, `start_url: "/"`
- **`sw.js`** registered from `layout.html` — stale-while-revalidate for:
  - `/itinerary/{id}` (HTML)
  - `/itinerary/{id}/pdf` + `/itinerary/{id}/visa-checklist.pdf` (binary)
  - `/static/css/`, `/static/js/`, fonts
  - OpenStreetMap tile URLs (`*.tile.openstreetmap.org`)
- First visit caches. Subsequent visits work offline.
- The cached **PDF is the most resilient fallback** — fully self-contained, works in airplane mode without any tiles.

---

## 14. PDFs (Flying Saucer)

Two PDFs ship in v1.0:

### Itinerary PDF (`itinerary-pdf.html`)

Print-optimised XHTML. **No flexbox or grid** (Flying Saucer limitation) — use tables for layout.

- Cover page: trip title, dates, traveller count, total cost
- Per day: theme, date, day total, activities table
- Booking strip: selected flight + hotel
- Visa summary block (if applicable)
- No interactive maps (deferred to v1.1)

### Visa Checklist PDF (`visa-checklist-pdf.html`)

- Cover page: applicant name placeholder, visa type, application deadline
- Documents checklist with notes
- Common rejection reasons (so applicant addresses them)
- **AI-generated cover letter** mentioning actual itinerary days
- VFS centre list
- Footer with `lastVerified` date + official source URL

---

## 15. Data Model

```java
enum Passport { IN, US, GB, EU, OTHER }
enum TripMode { BACKPACKER, COUPLE, FAMILY, GROUP, SENIOR }

// WizardInput — validated DTO
@Valid Passport passport;
@NotBlank String destination;
@NotNull LocalDate startDate, endDate;
@Min(1) @Max(20) int adults;
@Min(0) @Max(10) int children;
List<@Min(0) @Max(17) Integer> childAges;
@Min(50000) int budgetTotalLocal;
@NotNull TripMode mode;
List<String> interests;
List<String> dietary;
String accommodationTier;     // hostel|guesthouse|mid|boutique|luxury
String pace;                  // relaxed|balanced|packed
boolean wantFlights, wantHotels;
// Mode-specific (Family)
boolean needsStrollerAccess, showMedicalProximity;
// Mode-specific (Senior)
String mobilityLevel;         // active|moderate|limited
boolean preferDirectFlights;

// VisaRequirement — from resources/visa-data/*.json
Passport passport;
String destination;
boolean required;
String visaType;
ProcessingDays processingDays;  // min, typical, max
int feeEur, serviceFeeEur;
int validityDays;
String entries;
List<String> applyCenters;
String appointmentAvailability;
List<VisaDocument> requiredDocuments;
List<String> commonRejectionReasons;
String officialSourceUrl;
LocalDate lastVerified;

// Itinerary
UUID id;
WizardInput input;
Instant generatedAt;
List<ItineraryDay> days;
int totalEstimatedCostLocal, totalEstimatedCostEur;
List<String> highlights;
List<FlightSuggestion> flightOptions;
List<HotelSuggestion> hotelOptions;
String selectedFlightId, selectedHotelId;
VisaRequirement visa;                  // embedded snapshot
String aiGeneratedCoverLetter;

// ItineraryDay
int dayNumber;
LocalDate date;
String theme;
int estimatedDailyCostLocal;
List<Activity> activities;
DayMap map;                            // bounding box + center

// Activity — now with coords
String time;                           // "09:00"
String type;                           // sightseeing|food|transport|shopping|nature
String name;
String description;
String location;                       // neighbourhood / address
double lat, lng;                       // WGS84
boolean coordinatesValidated;          // server-side geocoded
int estimatedCostLocal;
int durationMinutes;
List<String> tags;
List<String> dietaryFlags;             // veg|halal|jain etc when food type

// DayMap
double centerLat, centerLng;
double[][] boundingBox;                // [[swLat,swLng],[neLat,neLng]]
```

---

## 16. Configuration (`application.properties`)

```properties
# --- Active AI provider (default Gemini free tier) ---
spring.ai.google.gemini.api-key=${GEMINI_API_KEY}
spring.ai.google.gemini.chat.options.model=gemini-2.5-flash

# --- Anthropic (alternate) — uncomment to enable ---
# spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
# spring.ai.anthropic.chat.options.model=claude-sonnet-4-6

# --- App config ---
weaveyourtrip.max-trip-days=14
weaveyourtrip.visa-buffer-days=7
weaveyourtrip.visa-data-path=classpath:/visa-data/
weaveyourtrip.visa-verifier-cron=0 0 3 * * *
weaveyourtrip.fx-rates-url=https://api.frankfurter.app/latest
weaveyourtrip.nominatim-url=https://nominatim.openstreetmap.org
weaveyourtrip.nominatim-rate-limit-ms=1100
weaveyourtrip.generation-cap-per-ip-per-day=20      # cost guard

# --- Server ---
server.port=8080
server.tomcat.threads.max=200
spring.thymeleaf.cache=false
spring.threads.virtual.enabled=true              # Java 21 virtual threads

# --- Logging ---
logging.level.com.weaveyourtrip=INFO
logging.level.org.springframework.ai=DEBUG
```

---

## 17. Monetisation (placeholder hooks in v1.0)

| Channel | Setup | Activates when |
|---|---|---|
| **Travel insurance** | Bajaj Allianz International link in visa card + itinerary footer | Affiliate account approved (week 4–6) |
| **Visa service affiliate** | Atlys / OneVasco link on visa card | Affiliate approved |
| **Flights** | Travelpayouts redirect on Skyscanner URL | Aggregator account approved |
| **Hotels** | Travelpayouts → Booking redirect | Same |
| **eSIM (Airalo)** | "Stay connected" section in itinerary | v1.1 |
| **Premium (v2.0)** | Visa appointment finder, taste profile, multi-passport | Post-MVP |

v1.0 puts the links in place with affiliate parameters empty. Activation = one-line config change once accounts approved.

---

## 18. Build Plan (6 weeks)

Approximate, single-developer pace. Each week ends with something demoable.

### Week 1 — Foundations + visa intel

**Day 1 — DONE (2026-06-07):**
- ✅ Maven scaffold via Spring Initializr (Spring Boot **3.4.4**, Java 21)
- ✅ Dependencies wired: Web, WebFlux, Thymeleaf, JPA, Flyway, Validation, Actuator, DevTools, Lombok, Mail
- ✅ Spring AI BOM 1.0.0 + `spring-ai-starter-model-google-genai`
- ✅ Caffeine + Flying Saucer 9.11.4 added manually
- ✅ Project lives at `/workspace/` (synced from `/Users/insign/dev/lab/weaveyourtrip/`)
- ✅ Main class renamed `WeaveyourtripApplication` → `WeaveYourTripApplication` for brand consistency
- ✅ Test starter consolidated to single `spring-boot-starter-test` + `reactor-test`

**Day 2 — Next up:**
- [ ] Get Gemini API key from https://aistudio.google.com/apikey
- [ ] Create `compose.yml` for local Postgres (port 5432, DB `weaveyourtrip`, password `dev`)
- [ ] Create `application.yml` (replace `application.properties`) with `local` + `prod` profiles
- [ ] First Flyway migration `V1__initial_schema.sql` — `itinerary`, `wizard_input`, `visa_correction` tables with JSONB columns
- [ ] AI smoke test — confirm `ChatClient` can call Gemini and return text

**Day 3 — Domain + visa service:**
- [ ] Port prototype CSS into `src/main/resources/static/css/style.css`
- [ ] `HomeController` + landing page (port `index-visa.html`)
- [ ] Domain enums: `Passport`, `TripMode`
- [ ] `WizardInput` DTO with Bean Validation
- [ ] `VisaRequirement` + `VisaDocument` records

**Day 4–5 — Visa flow:**
- [ ] Move `/workspace/visa-data/*.json` → `src/main/resources/visa-data/`
- [ ] Hand-verify IN-SCHENGEN.json + IN-GB.json values against current VFS pages (current data is **acid-test placeholder**, must be verified)
- [ ] `VisaService` + JSON loader at startup
- [ ] `VisaController` with HTMX visa-card endpoint
- [ ] `WizardController` step 1 (passport picker) + step 2 (destination + visa card)
- [ ] **Demoable:** Pick passport → type destination → see real visa card

### Week 2 — Wizard completion
- Wizard steps 3 (dates+group), 4 (mode), 5 (style+dietary+mode-specific), 6 (review)
- Session-scoped `WizardInput` bean
- Jakarta Bean Validation on each step submission
- Mode-specific field rendering in step 5
- **Demoable:** Walk all 6 steps, hit Generate (placeholder), see filled review summary

### Week 3 — AI generation + streaming
- `AiService` with Spring AI + Gemini Flash
- Prompt template assembly from `WizardInput` + `VisaRequirement`
- NDJSON streaming parser
- `ItineraryService` storage (in-memory `ConcurrentHashMap`)
- SSE endpoint + HTMX SSE swap
- `Itinerary` data model + day-by-day rendering
- Cost cap per IP per day
- **Demoable:** Generate a real itinerary, see days appear progressively

### Week 4 — Maps + currency + cultural context
- `GeocodingService` (Nominatim + Caffeine cache + rate limit)
- Bounding-box validation of AI-provided coordinates
- Leaflet integration in `itinerary.html` — markers + polyline per day
- `CurrencyService` (Frankfurter.app cached daily)
- Multi-currency display (INR primary, EUR secondary)
- Cultural context blocks in prompt
- **Demoable:** Itinerary view shows interactive map per day, all prices in INR

### Week 5 — Flights/Hotels + Booking redirects
- `FlightSuggestion` + `HotelSuggestion` data model
- AI prompt extension for flights/hotels
- `BookingUrlService` (Skyscanner + Booking URL builders)
- `SelectionController` + `flights.html` + `hotels.html` templates
- Booking strip in itinerary view
- **Demoable:** Full end-to-end — wizard → AI generation → flight pick → hotel pick → itinerary with selected bookings

### Week 6 — PDFs + PWA + polish
- `PdfExportService` (Flying Saucer)
- `itinerary-pdf.html` template (table-based for Flying Saucer constraints)
- `visa-checklist-pdf.html` template
- AI cover letter generation (`AiService.generateCoverLetter`)
- `manifest.json` + service worker
- Visa data daily verifier job
- Beta deploy (Fly.io / Railway / Render — any will do)
- Verification checklist run
- **Demoable / shippable:** Beta-ready product

---

## 19. Verification Checklist

- [ ] `./mvnw spring-boot:run` → landing loads, passport-first hook visible
- [ ] Step 1: passport picker, India selectable, others show "coming soon"
- [ ] Step 2: typing destination triggers HTMX visa card load
- [ ] Visa card shows: required Y/N, processing days, fee in INR, earliest viable departure
- [ ] Step 3: date picker pre-fills earliest viable date, budget displays in INR with EUR conversion
- [ ] Step 4: 5 mode cards (3 enabled in v1.0), selection auto-advances
- [ ] Step 5: mode banner shows at top, mode-specific fields render correctly
- [ ] Step 6: review summary includes passport, visa, mode rows
- [ ] Generate: itinerary days appear progressively via SSE
- [ ] Each day card has a Leaflet map with numbered markers + polyline route
- [ ] Maps work after one online visit, then offline (PWA cache)
- [ ] Generated trip duration fits within visa validity (max 14 days, max visa stay)
- [ ] Itinerary respects dietary preferences in restaurant choices
- [ ] Activity coordinates validated against destination bounding box
- [ ] Booking strip: flight + hotel selected, "Change" works, "Book" opens Skyscanner/Booking
- [ ] All money in INR primary, EUR secondary
- [ ] Itinerary PDF: tables render, no truncation, visa summary present
- [ ] Visa Checklist PDF: documents + AI-generated cover letter, ready to print
- [ ] All visa data displays `lastVerified` date + report-incorrect link
- [ ] PWA: itinerary URL loads in airplane mode after one online visit
- [ ] Validation: missing required field shows inline HTMX error
- [ ] Per-IP rate limit caps generation at 20/day

---

## 20. Design Constraints for Future Phases

### Mixed-passport group planning (v3+ — the true moat)

**The pain:** Five friends planning Bali — 2 Indian passports, 1 US, 1 UK, 1 Singaporean — each flying from a different city. Wanderlog / Stippl / Mindtrip / ChatGPT all assume *user = traveller = single passport*. Real-world groups face:

- Different visa requirements per member (some need it, some don't, different fees, different processing times)
- Different earliest viable departure dates — trip must wait for the slowest applicant
- Different origin cities → flights become rendezvous coordination, not out-and-back
- Different dietary defaults + currency displays per member
- Each member needs their own visa checklist PDF, tailored to their passport

**Why this is defensible:** Building for multi-passport groups requires architectural changes most apps will not make. The visa-first foundation we're laying in v1.0 maps directly onto this — most planners would have to start over. This is exactly the kind of feature that justifies premium pricing (₹999/month or per-trip fee).

**Architectural shape (v3 target):**

```
Trip {
  UUID id
  TripMember organizer
  List<TripMember> members
  String destination
  LocalDate proposedStart, proposedEnd
  Itinerary consolidatedItinerary   // single shared plan
  HotelSuggestion sharedHotel        // everyone stays together
  Instant createdAt
}

TripMember {
  UUID userId
  Passport passport                  // varies per member
  String origin                      // possibly different per member
  List<String> dietary               // contributes to group dietary union
  String preferredCurrency
  VisaRequirement visaRequirement    // computed from passport + destination
  String visaChecklistPdfUrl         // per-member, tailored
  FlightSuggestion flightSelection   // their own outbound from their origin
  String inviteStatus                // invited | accepted | declined
}
```

**Key UX shifts:**

1. **Trip Hub** replaces single-user itinerary view — all members' visa statuses, individual flights from different origins, shared hotel, single consolidated day-by-day plan
2. **Latest viable departure** = `max(member.earliestViableDeparture)` — slowest visa wins, all members see the same date
3. **Cultural union for activities** — restaurants must satisfy everyone (if any member is veg → restaurants need veg options; if any needs halal → flagged; if any has Jain preference → flagged). Conservative by default, members can opt out
4. **Multi-origin flight coordination** — "Where is everyone flying from?" → each member gets a flight selection screen from their origin → consolidation view shows "Everyone lands at DPS by Aug 5, 14:00"
5. **Currency per member** — same activities, but prices render in each member's currency. PDF respects per-member preference.
6. **N visa checklist PDFs** — one per member, each tailored to that member's passport corridor
7. **Real-time collaboration** — WebSockets or long-polling SSE for live updates ("Priya selected Singapore Airlines", "Voting on Day 5 activity: snorkel vs. temple")
8. **Voting + suggestions** — any member can propose swaps, others vote; itinerary updates when consensus reached

**Revenue multiplier:** Mixed-passport groups are high-trust, high-friction users — exactly the segment that pays. Insurance affiliate × N members. Visa-service affiliate × N members. Premium tier upgrade rate likely much higher than solo users.

**MVP-side preparation (no refactor needed at v3):**

The v1.0 data model already keeps passport at the **`WizardInput`** level, not embedded in **`Activity`** or **`ItineraryDay`**. This means:
- Activities are passport-agnostic ✓ (just place + time + description + cost + coords)
- Visa requirement is a property of the input, not the plan ✓
- The Trip → Members → Itinerary hierarchy *wraps* the existing model rather than replacing it ✓
- No data migration needed when v3 lands — existing single-traveller trips become "Trip with one member"

**Prerequisites that must land first:**
- User accounts + auth (v1.2)
- PostgreSQL migration (v1.2)
- Soft-passport visa corridors (v1.1) — so non-Indian members can be supported
- WebSocket / SSE infrastructure (Phase 5+ live-updates work)

**Decision now:** Keep `Itinerary.input.passport` as a single field for v1.0. **Do not embed passport-specific data inside Activity, ItineraryDay, or any per-day structure.** The wrap-don't-refactor path depends on it.

---

### Itinerary refinement (Phase 5+ — chat-driven edits)
AI must return JSON Patch (RFC 6902) operations, not regenerated itineraries. Prevents itinerary drift. Stable activity IDs needed (already in v1.0 data model via day number + activity index).

### Live updates & data freshness (Phase 5+)
Weather/visa-rule alerts: one scheduled job per region per hour fans out to users matching that region. Don't poll APIs per user. Geocode destinations once on creation when this phase arrives.

### Visa data integrity at scale (post-50 corridors)
Switch from hand-curation to structured scrape + LLM-verify. Monthly job pulls each official source, LLM extracts structured data, human reviews diffs only.

### Maps in PDF (v1.1+)
Either Mapbox Static API (~$2/1000 images, easy) or self-hosted `tileserver-gl-static` (free, infrastructure-heavier). Decide based on user volume at the time.

---

## 21. Deferred to Post-MVP (priority order)

1. **Soft-passport visa data** — US/GB/EU corridors (10–15 entries) — v1.1
2. **Couple, Group, Senior mode-specific fields** — v1.1
3. **Real affiliate IDs** — Travelpayouts (flights/hotels), insurance, visa services — v1.1 once approved
4. **Maps in PDF** — Mapbox Static API — v1.1
5. **User accounts + trip history** — needs DB migration — v1.2
6. **PostgreSQL migration** — replaces in-memory storage — v1.2
7. **Visa appointment finder** — premium feature, monitors VFS slots — v2.0
8. **Real flight/hotel APIs** — Travelpayouts first, direct providers later — v2.0
9. **Chat refinement** — JSON Patch-based, see constraints section — v2.0
10. **Live updates** — weather, visa rule changes, flight delays — Phase 5+
11. **🌟 Mixed-passport group planning** — multiple members with different passports, origins, dietary, currencies, visa requirements per member. **The true moat.** Architectural prerequisites: auth + PostgreSQL + per-member trip model. See §20 for full design — **v3.0**
12. **Push notifications** — needs native shell or paid web-push — Phase 5+
13. **Offline AI generation** — Ollama on-device — power-user feature
14. **eSIM affiliate** — Airalo / Holafly — v2.0
15. **Travel taste profile** — needs repeat-trip data — Phase 6+
16. **Collaborative single-passport group planning** — vote / share / invite within one passport — Phase 6+ (subset of #11)
17. **Mobile native apps** — Year 2 per blueprint
18. **B2B white-label** — once volume validates demand
