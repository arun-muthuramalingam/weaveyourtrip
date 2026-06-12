# WeaveYourTrip — Project Context for Claude

This file orients any Claude Code session opening this directory. **Always read this first** when picking up work.

---

## What this project is

**WeaveYourTrip** — an AI-powered travel planner with a **passport-first wizard**. The visa-aware step does real work for hard-passport travellers (Indian, Pakistani, Bangladeshi, Nigerian, etc.) and collapses to "✓ no visa needed" for soft-passport travellers (US, UK, EU). Cultural context (currency, dietary defaults, restaurant filters) applies to everyone.

Personal product project by **Arun Muthuramalingam** (`arun.muthuramalingam@bsi-software.com`), Spring Boot backend dev, new to HTMX/Thymeleaf.

**Tagline:** *Travel that starts where you do.*
**USP:** The AI travel planner built around your passport.
**Domain:** `weaveyourtrip.com`

---

## Where the canonical docs live

| Doc | What it is |
|---|---|
| [`docs/weaveyourtrip-mvp-plan.md`](docs/weaveyourtrip-mvp-plan.md) | **The plan.** Read this for full scope, build-week-by-week timeline, data model, API integrations, design constraints. Always check the "Last updated" date at the top. |
| [`docs/weaveyourtrip-blueprint.html`](docs/weaveyourtrip-blueprint.html) | Original product blueprint (visual roadmap, 7 phases). Reference only — superseded by mvp-plan for MVP scope. |
| [`prototype/`](prototype/) | Static HTML mockups of the final UX. Visual reference for porting into Thymeleaf templates. Canonical files: `index-visa.html`, `wizard-visa.html`, `flights.html`, `hotels.html`, `itinerary.html`. Legacy mode-specific wizards (`wizard-backpacker.html`, etc.) are pre-pivot reference. |
| [`visa-data/`](visa-data/) | Hand-curated corridor JSON files. **Current values are acid-test placeholder — must be human-verified against official VFS pages before MVP launch.** Will move to `src/main/resources/visa-data/` during Spring Boot integration. |

---

## Current state (2026-06-07)

### Done
- Brand rename TripForge → WeaveYourTrip across all files
- Prototype complete (passport → visa card → 6-step wizard → flights → hotels → itinerary with India→Paris demo + INR currency + visa-aware UI + maps spec)
- MVP plan v2 finalised including:
  - 6-step wizard (passport, destination+visa, dates+group, mode, style+dietary, review)
  - Maps integration design (Leaflet + OpenStreetMap, one map per day)
  - Mixed-passport group planning as v3 moat (architectural constraint: keep passport at `WizardInput` level only, never inside Activity or ItineraryDay)
- Spring Boot project initialized at this repo root:
  - Spring Boot **3.4.4** (downgraded from 4.0.6 for Spring AI 1.0 compatibility)
  - Java **21**, Maven
  - Spring AI **1.0.0** + `spring-ai-starter-model-google-genai` (consumer Gemini API, free tier)
  - JPA + Flyway + PostgreSQL driver + Thymeleaf + Validation + WebMVC + WebFlux + Actuator + DevTools + Lombok + Mail
  - Caffeine + Flying Saucer 9.11.4 (manual `pom.xml` adds)
  - Tests consolidated to `spring-boot-starter-test` + `reactor-test`
  - Main class: `WeaveYourTripApplication` at `com.weaveyourtrip`

### Where to resume (Day 2 of Week 1)
1. Get Gemini API key from https://aistudio.google.com/apikey, export as `GEMINI_API_KEY` env var
2. Run `./mvnw clean compile` on Mac to verify pom resolves (if Spring AI version errors, check https://github.com/spring-projects/spring-ai/releases for latest 1.0.x patch)
3. Create `compose.yml` for local Postgres (port 5432, DB `weaveyourtrip`, password `dev`)
4. Replace `application.properties` with `application.yml` (local + prod profiles)
5. First Flyway migration `src/main/resources/db/migration/V1__initial_schema.sql` — `itinerary`, `wizard_input`, `visa_correction` tables with JSONB for content blobs
6. AI smoke test: write a one-shot ChatClient call to confirm Gemini wiring works

---

## Working conventions

- **Workspace canonical location:** `/workspace/` in this Linux sandbox. Mac copy at `/Users/insign/dev/lab/weaveyourtrip/`. Sync direction: edit here, `rsync -av --exclude='.git' --exclude='target' /workspace/ /Users/insign/dev/lab/weaveyourtrip/` when needed on Mac.
- **Architecture is server-rendered HTMX, NOT a SPA.** Controllers return HTML fragments via Thymeleaf for wizard steps and visa cards. Only `/api/generate`, `/api/geocode`, and SSE `/itinerary/{id}/stream` return JSON. Do not design REST APIs for endpoints that should return HTML.
- **Service-first within HTMX architecture:** build domain model → services → controllers (HTML) → templates. Unit-test services without HTTP.
- **Storage strategy:** PostgreSQL from day one (not H2). Local via Docker Compose, prod via Fly Postgres. JSONB columns for itinerary content.
- **AI model default:** Gemini 2.5 Flash via `spring-ai-starter-model-google-genai`. Alternates wired by config swap only.
- **Deployment target:** Fly.io Mumbai region (`bom`) for latency to Indian audience.

---

## Critical design constraints (don't break these)

1. **Activities must stay passport-agnostic.** The v3 moat (mixed-passport group planning) depends on `Activity`, `ItineraryDay`, and per-day structures having **zero passport-specific fields**. Passport lives at `WizardInput` level only. Cultural context is injected at the AI prompt layer, not the data layer.
2. **NDJSON streaming for itineraries** — one JSON object per line, not partial JSON. Server parses line-by-line, emits SSE events per day. Avoids fragile mid-stream JSON parsing.
3. **Per-IP generation rate limit** — cap at 20/day to prevent runaway AI costs. Implementation deferred but architecture must support it.
4. **Visa data integrity** — `lastVerified` date must be displayed on every visa card and PDF. Crowdsourced corrections via `POST /visa/.../report-error`. Daily verifier job hashes source URLs and emails on diff.

---

## Decision archive (don't re-litigate)

| Decision | Why |
|---|---|
| Visa-first niche + soft-passport flow in same product | Visa-first alone was too narrow; merged design widens TAM without losing wedge |
| Mode picker as step 4 of wizard | After group composition, before style — provides natural context for which modes are sensible |
| `WeaveYourTrip` over `Weyve` | `weyve.com` was €6,000 premium; `weaveyourtrip.com` is €10/yr. Cost/benefit doesn't justify pre-validation. |
| Spring Boot 3.4.4 over 4.0.6 | Spring AI 1.0 GA targets Spring Boot 3.4. Boot 4.0 compatibility unproven. |
| PostgreSQL over H2 | JSONB needed for itinerary blob. Migration always finds edge cases — avoid by using same DB locally and in prod. |
| Leaflet + OpenStreetMap over Mapbox/Google | Free, no API key, sufficient for MVP. Upgrade path open if needed. |
| Travelpayouts over direct Skyscanner Partners | Single token covers many providers, lower approval threshold. (Deferred to v1.1.) |

---

## Don't do

- Don't add features beyond MVP scope without first checking the plan's `Deferred to Post-MVP` section
- Don't bikeshed names, taglines, or domain decisions — locked
- Don't introduce passport-specific fields into per-day or per-activity data structures — breaks v3 moat
- Don't use H2 — PostgreSQL only, local via `compose.yml`
- Don't write the API contract before the service — service-first within HTMX architecture
- Don't generate documentation files (.md) unless explicitly requested
