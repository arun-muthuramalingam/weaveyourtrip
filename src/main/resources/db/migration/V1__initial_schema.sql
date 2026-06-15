-- =============================================================================
-- V1 — initial schema for WeaveYourTrip
--
-- Two tables for MVP v1.0:
--   1. itinerary         — top-level aggregate, JSONB for AI-generated content
--   2. visa_correction   — crowdsourced corrections to hand-curated visa data
--
-- JPA entities will map onto these in week 1, day 3+.
-- Hibernate ddl-auto=validate ensures schema/code stay in sync.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- itinerary
-- -----------------------------------------------------------------------------
CREATE TABLE itinerary (
    id                    UUID            PRIMARY KEY,
    passport              TEXT            NOT NULL,
    destination           TEXT            NOT NULL,
    start_date            DATE            NOT NULL,
    end_date              DATE            NOT NULL,
    trip_mode             TEXT            NOT NULL,
    input                 JSONB           NOT NULL,           -- full WizardInput snapshot
    days                  JSONB,                              -- day-by-day plan
    flights               JSONB,                              -- AI-suggested flight list
    hotels                JSONB,                              -- AI-suggested hotel list
    visa                  JSONB,                              -- VisaRequirement snapshot
    selected_flight_id    TEXT,
    selected_hotel_id     TEXT,
    cover_letter          TEXT,                               -- AI-generated for visa application
    total_cost_local      INTEGER,
    total_cost_eur        INTEGER,
    generated_at          TIMESTAMPTZ     NOT NULL            DEFAULT now(),
    created_ip            INET                                -- for per-IP rate limiting
);

CREATE INDEX ix_itinerary_passport_dest
    ON itinerary (passport, destination);

CREATE INDEX ix_itinerary_generated_at
    ON itinerary (generated_at DESC);

-- For rate limiting: count itineraries created from a given IP today.
CREATE INDEX ix_itinerary_ip_day
    ON itinerary (created_ip, (generated_at::date));


-- -----------------------------------------------------------------------------
-- visa_correction
-- -----------------------------------------------------------------------------
CREATE TABLE visa_correction (
    id                    UUID            PRIMARY KEY,
    passport              TEXT            NOT NULL,
    destination           TEXT            NOT NULL,
    report                TEXT            NOT NULL,
    reporter_ip           INET,
    reported_at           TIMESTAMPTZ     NOT NULL            DEFAULT now(),
    reviewer_status       TEXT            NOT NULL            DEFAULT 'PENDING'
                                          CHECK (reviewer_status IN ('PENDING', 'APPLIED', 'REJECTED'))
);

-- Partial index — owner queries PENDING reports during weekly review.
CREATE INDEX ix_visa_correction_pending
    ON visa_correction (reported_at DESC)
    WHERE reviewer_status = 'PENDING';

CREATE INDEX ix_visa_correction_passport_dest
    ON visa_correction (passport, destination);
