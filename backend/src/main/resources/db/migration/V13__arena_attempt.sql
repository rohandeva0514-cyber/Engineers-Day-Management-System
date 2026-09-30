-- One participant's run at the Debugging Arena.
--
-- ONE REGISTRATION, ONE ATTEMPT
--
-- The UNIQUE on registration_id is the whole one-attempt rule, and it is a
-- database fact rather than an application check. Refreshing, reopening the
-- browser, or checking in from a second machine cannot produce a second attempt
-- because a second row cannot exist - not because some service remembered to look
-- first. A SELECT-then-INSERT check has a window between the two statements where
-- two simultaneous check-ins both find nothing and both insert; this does not.
--
-- WHY THE ROW IS CREATED AT CHECK-IN, NOT AT START
--
-- The state machine starts at INITIALIZED, written when a student exchanges their
-- access code, and moves to ACTIVE only when they press Start Mission. Nothing
-- about INITIALIZED costs the student anything: started_at and expires_at stay
-- NULL, so the 45 minutes has not begun, and they may change language, close the
-- laptop, or move to another machine freely.
--
-- What it buys is somewhere for the session to live. Session takeover needs a
-- persisted token hash and a persisted counter from the very first exchange, and
-- with no row until Start those would need a second table holding a second
-- identity for the same student. One row, one hash, one counter.
--
-- THE RAW SESSION TOKEN IS NEVER STORED
--
-- session_token_hash holds SHA-256 of the token and nothing else - 64 lower-case
-- hex characters, which the CHECK enforces. Reading this table therefore does not
-- let anyone resume a session. The regex is a shape guard against any future write
-- path storing the raw value by mistake, the same role ck_registration_access_code
-- plays in V9.
--
-- NO PROBLEM BANK TABLES
--
-- Deliberately none, and none later. The five JSON banks in resources/debugging/
-- are the source of truth, which is what keeps corrected_code and hidden_tests out
-- of PostgreSQL entirely: a database compromise cannot leak an answer that the
-- database has never held. Per-attempt problem state (drafts, per-problem status)
-- arrives in Phase C as its own table keyed by the bank's own string id.
--
-- NOTHING IS DESTRUCTIVE
--
-- A new table. No existing row is read, written or deleted.

CREATE TABLE arena_attempt (
    id                  BIGSERIAL    PRIMARY KEY,

    -- THE one-attempt constraint. Everything else here is detail.
    registration_id     BIGINT       NOT NULL UNIQUE REFERENCES registration (id),

    -- Denormalised from the registration purely so attempt queries do not have to
    -- join to answer "whose attempt is this". It is written once, at creation, from
    -- the registration itself and never updated.
    participant_id      BIGINT       NOT NULL REFERENCES participant (id),

    state               VARCHAR(16)  NOT NULL DEFAULT 'INITIALIZED',

    -- NULL until the participant picks one. Locked at Start Mission; see the
    -- ck_arena_attempt_started constraint, which makes "ACTIVE with no language"
    -- unrepresentable rather than merely prevented in Java.
    language            VARCHAR(16),

    -- SHA-256 of the session token, hex. Never the token.
    --
    -- VARCHAR rather than CHAR(64). PostgreSQL blank-pads CHAR, which is a
    -- liability for a value that is only ever compared for exact equality, and
    -- the length is pinned precisely by ck_arena_attempt_session_hash below.
    session_token_hash  VARCHAR(64),
    session_issued_at   TIMESTAMPTZ,
    session_expires_at  TIMESTAMPTZ,

    -- How many times a later check-in replaced an earlier session. Zero for the
    -- overwhelming majority. A non-zero value is not proof of anything by itself -
    -- a crashed browser produces one - but it is the signal an organiser needs to
    -- notice a shared code, which is why takeover is counted rather than silent.
    session_takeovers   INT          NOT NULL DEFAULT 0,

    started_at          TIMESTAMPTZ,

    -- Stamped once, from the server clock, at Start Mission. Never recomputed, so
    -- an admin changing arena_control.duration_seconds mid-event cannot move the
    -- finish line for someone already running.
    expires_at          TIMESTAMPTZ,

    -- Phase E (final submission, expiry, scoring) fills these. Declared now so the
    -- table's shape is settled and the scoring phase is not three ALTERs.
    finalized_at        TIMESTAMPTZ,
    final_state_reason  VARCHAR(32),
    score               INT,
    solved_count        INT,

    last_seen_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_arena_attempt_state CHECK (state IN
        ('INITIALIZED', 'ACTIVE', 'SUBMITTED', 'EXPIRED', 'TERMINATED')),

    -- The five supported languages. This duplicates the ArenaLanguage enum on
    -- purpose: it is a shape guard, not a policy table. A sixth language already
    -- requires a problem bank, a judge runtime and a deploy, so the one-line
    -- migration to widen this is not the part that makes it expensive.
    CONSTRAINT ck_arena_attempt_language CHECK (language IS NULL OR language IN
        ('c', 'cpp', 'java', 'python', 'javascript')),

    -- An attempt that has left INITIALIZED must have a language, a start and a
    -- deadline. This is what makes "running, but we do not know until when"
    -- impossible to store.
    CONSTRAINT ck_arena_attempt_started CHECK (
        state = 'INITIALIZED'
        OR (language IS NOT NULL AND started_at IS NOT NULL AND expires_at IS NOT NULL)),

    CONSTRAINT ck_arena_attempt_window CHECK (expires_at IS NULL OR expires_at > started_at),

    -- Hex, lower case, 64 characters. A raw token would not match.
    CONSTRAINT ck_arena_attempt_session_hash CHECK (
        session_token_hash IS NULL OR session_token_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_arena_attempt_takeovers CHECK (session_takeovers >= 0)
);

-- Session lookup happens on every protected arena request, so it is the one index
-- that has to exist. Unique because two attempts sharing a token hash would mean
-- one token authorising two people.
CREATE UNIQUE INDEX uq_arena_attempt_session
    ON arena_attempt (session_token_hash)
 WHERE session_token_hash IS NOT NULL;

-- The admin roster's filter, and Phase E's expiry sweep.
CREATE INDEX idx_arena_attempt_state ON arena_attempt (state);
CREATE INDEX idx_arena_attempt_participant ON arena_attempt (participant_id);

COMMENT ON TABLE arena_attempt IS
    'One participant''s Debugging Arena run. UNIQUE(registration_id) is the one-attempt rule.';
COMMENT ON COLUMN arena_attempt.session_token_hash IS
    'SHA-256 of the session token, hex. The raw token is never stored.';
COMMENT ON COLUMN arena_attempt.session_takeovers IS
    'Times a later check-in replaced an earlier session. Non-zero is worth an organiser''s attention.';
COMMENT ON COLUMN arena_attempt.expires_at IS
    'Stamped once at Start Mission. Never recomputed, so a duration change cannot move a running deadline.';

-- The V5 lockdown. This table holds session material and, from Phase C, every
-- participant's draft code.
ALTER TABLE arena_attempt ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    browser_role text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON public.arena_attempt FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.arena_attempt_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;
