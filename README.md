# WeaveYourTrip

> *Travel that starts where you do.*

An AI-powered travel planner with a **passport-first wizard**. The visa-aware
step does real work for hard-passport travellers (🇮🇳 Indian, 🇵🇰 Pakistani,
🇧🇩 Bangladeshi, 🇳🇬 Nigerian, etc.) and collapses to *"✓ no visa needed"* for
soft-passport travellers (🇺🇸 US, 🇬🇧 UK, 🇪🇺 EU). Cultural context — currency,
dietary defaults, restaurant filters — applies to everyone.

**Status:** Pre-MVP build · MVP target is week-6 deploy.

---

## What it does

1. Asks for **your passport** before anything else
2. Surfaces **visa requirements** the moment you pick a destination — fee,
   processing time, document checklist, earliest viable departure date
3. Captures the rest of the trip via a 6-step wizard (dates, group, mode, style)
4. **Generates an AI itinerary** in 15–25 seconds — day-by-day plan, lat/lng
   coordinates for every activity, AI-suggested flights + hotels with Skyscanner
   and Booking.com deep-link redirects
5. Renders an **interactive Leaflet map per day** with numbered markers + route
6. **Multi-currency display** — INR primary for Indian travellers with EUR
   secondary; Frankfurter.app FX rates cached daily
7. *(Coming)* PDF download for the itinerary + a visa-application document
   checklist with AI-generated cover letter

See **[`docs/weaveyourtrip-mvp-plan.md`](docs/weaveyourtrip-mvp-plan.md)** for
the full plan, **[`PROGRESS.md`](PROGRESS.md)** for what's shipped vs. todo,
and **[`CLAUDE.md`](CLAUDE.md)** for project orientation and key decisions.

---

## Tech stack

| Layer | Choice | Version |
|---|---|---|
| Backend | Spring Boot | 3.4.4 |
| JDK | Java | 21 |
| Templates | Thymeleaf | (Boot-managed) |
| Interactivity | HTMX | 2.0 (CDN) |
| AI | Spring AI · Gemini 2.5 Flash | 1.0.0 |
| Maps | Leaflet + OpenStreetMap | 1.9.4 (CDN) |
| Currency | Frankfurter.app | (no key) |
| Geocoding | Nominatim | (no key) |
| Persistence | PostgreSQL + Spring Data JPA + Flyway | 16 |
| Cache | Caffeine | (Boot-managed) |
| PDF *(coming)* | Flying Saucer + OpenPDF | 9.11.4 |
| Build | Maven Wrapper | — |

No build step, no npm — HTMX + Leaflet load via CDN.

---

## Running locally

### Prerequisites

- **Java 21+** (`java --version`)
- **Docker Desktop** or any compose-compatible runtime (for PostgreSQL)
- **Gemini API key** — free tier at https://aistudio.google.com/apikey
  (15 RPM, 1M TPM, 1500 requests/day)

### Two ways to run

#### A. Dev mode — fast iteration, needs Java + Maven on host

```bash
# 1. Start PostgreSQL only (the app stays out of Docker)
docker compose up -d db

# 2. Export your Gemini key for this shell
export GEMINI_API_KEY=AIza...your-key-here

# 3. Run the app — Flyway migrations apply automatically on boot
./mvnw spring-boot:run
```

> **Tip:** Spring Boot 3.4's built-in Docker Compose support auto-starts the
> `db` service when you run `./mvnw spring-boot:run`, so step 1 is technically
> optional during dev.

#### B. Full-container mode — no Java needed on host

```bash
# 1. Drop your key in a .env file at the project root
cp .env.example .env
# edit .env, paste your Gemini key

# 2. Bring everything up
docker compose --profile app up --build
```

Both modes serve the app at **http://localhost:8080**. Open it, click
*Start with your passport →*, walk the wizard.

### Stopping

```bash
# Dev mode: Ctrl-C the spring-boot process, then:
docker compose down            # stops db, keeps data
docker compose down -v         # also wipes the named volume

# Container mode: Ctrl-C the compose foreground, or:
docker compose --profile app down
```

---

## Project structure

```
weaveyourtrip/
├── README.md                         ← you are here
├── CLAUDE.md                          orientation for AI sessions
├── PROGRESS.md                        shipped vs todo, day by day
├── HELP.md                            Spring Initializr's generic help
├── compose.yml                        local PostgreSQL 16
├── pom.xml
├── docs/
│   ├── weaveyourtrip-mvp-plan.md     ★ full implementation plan
│   └── weaveyourtrip-blueprint.html  original product blueprint
├── prototype/                         static HTML mockups (visual reference)
└── src/
    ├── main/
    │   ├── java/com/weaveyourtrip/
    │   │   ├── WeaveYourTripApplication.java
    │   │   ├── config/                Spring config (session-scoped beans)
    │   │   ├── controller/            @Controller — returns HTML fragments
    │   │   ├── model/                 enums, records, JPA entity
    │   │   ├── repository/            Spring Data JPA interfaces
    │   │   └── service/               AI, visa, currency, geocoding, bookings, …
    │   └── resources/
    │       ├── application.yml        local + prod profiles
    │       ├── db/migration/          Flyway V1__initial_schema.sql
    │       ├── visa-data/             hand-curated corridor JSON (IN-SCHENGEN, IN-GB)
    │       ├── static/                CSS, JS, SVG, manifest
    │       └── templates/             Thymeleaf — wizard steps + itinerary
    └── test/java/com/weaveyourtrip/   JUnit 5 tests
```

---

## Testing

```bash
# Full test suite (needs Postgres up, optionally GEMINI_API_KEY)
./mvnw test

# VisaService in isolation — no DB, no AI key needed
./mvnw test -Dtest=VisaServiceTest

# AI smoke test — skipped automatically when GEMINI_API_KEY is unset
./mvnw test -Dtest=AiSmokeTest
```

---

## Configuration

All settings live in `src/main/resources/application.yml`. Three profile blocks:

- **default** — shared across all environments (Spring AI, Flyway, JPA config)
- **`local`** — Docker Compose Postgres, verbose logging, Thymeleaf cache off
- **`prod`** — credentials from `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}` env vars

Active profile defaults to `local`. Override with `SPRING_PROFILES_ACTIVE=prod`.

Key env vars:

| Variable | Required | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | Yes (for AI) | Spring AI Gemini consumer API key |
| `SPRING_PROFILES_ACTIVE` | No (defaults to `local`) | `local` or `prod` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Only in `prod` | PostgreSQL connection |

App-specific config under `weaveyourtrip.*` — max trip days, visa buffer,
rate limit cap, Nominatim user-agent, etc.

---

## What's NOT in MVP v1.0

Major features deferred to v1.1+:

- Soft-passport visa flows (US/UK/EU corridors)
- User accounts + trip history
- Real flight/hotel inventory APIs (Travelpayouts, Skyscanner Partners, Booking Connect)
- Affiliate IDs on booking URLs
- Visa appointment slot tracking (premium feature)
- Mobile native apps
- **🌟 Mixed-passport group planning** (v3 — the architectural moat; see plan §20)

See `PROGRESS.md` for the day-by-day milestone tracker.

---

## License

Private project — Arun Muthuramalingam · 2026
