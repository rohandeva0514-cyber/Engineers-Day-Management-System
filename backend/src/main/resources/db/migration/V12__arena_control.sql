-- The Debugging Arena's lifecycle switch.
--
-- WHAT IT IS
--
-- Operational state an organiser flips on event day: is the arena OFFLINE (nobody
-- may check in), ACTIVE (the competition is running), or ENDED (the event is over
-- and every remaining attempt is finalized).
--
-- WHY A TABLE AND NOT A CONFIG PROPERTY
--
-- Same reasoning as system_setting in V8: this is state an organiser changes while
-- the service is running. It has to survive a restart and be visible to every
-- instance at once, neither of which a property file does.
--
-- WHY NOT A COLUMN ON system_setting
--
-- system_setting is the master switch for REGISTRATION, which is a different
-- lifecycle with a different owner and a different meaning. Folding the arena into
-- it would mean the row that stops entries and the row that runs the competition
-- are the same row, and a mistake in one would be a mistake in the other.
--
-- WHY KEYED BY event_id
--
-- Not a singleton. The arena is a property of an event that is run against a
-- terminal, and V9 already established `event.requires_access_code` as the data
-- that says which events those are. Keying on event_id means a second terminal-run
-- event later is an INSERT, not a second mechanism - the same reasoning that let
-- Tech Debate become solo in V4 without touching Java.
--
-- THE SEED IS DERIVED, NOT NAMED
--
-- The INSERT selects from `requires_access_code` rather than hardcoding
-- 'debugging'. Today that is exactly one row. Nothing in this file names the
-- Debugging event, and nothing in Java does either.
--
-- STATUS, NOT A BOOLEAN
--
-- Three states, because STOP and END are genuinely different operations: STOP is
-- "pause, something is wrong, I will resume", END is "the event is over, finalize
-- everyone". A boolean would collapse them, and collapsing them turns a projector
-- failure into a mass submission.
--
-- NOTHING IS DESTRUCTIVE
--
-- A new table and one row per terminal-run event. No existing row is read, written
-- or deleted, and this is safe to apply to a database already holding
-- registrations.

CREATE TABLE arena_control (
    event_id         VARCHAR(32)  PRIMARY KEY REFERENCES event (id),

    status           VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',

    -- 2700 = 45 minutes. Stored rather than hardcoded so a future event can run a
    -- different length without a deploy. Attempts stamp their own expires_at at
    -- start, so changing this never moves a running participant's deadline.
    duration_seconds INT          NOT NULL DEFAULT 2700,

    opened_at        TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,

    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Who flipped it. The full audit trail arrives with the admin console; this is
    -- the one field worth having from the first migration, because "who started
    -- the arena early" is the question that gets asked afterwards.
    updated_by       VARCHAR(64),

    CONSTRAINT ck_arena_status CHECK (status IN ('OFFLINE', 'ACTIVE', 'ENDED')),

    -- A floor and a ceiling rather than an exact value: it stops a typo setting a
    -- 45-second or 45-hour mission, without freezing the duration at 45 minutes.
    CONSTRAINT ck_arena_duration CHECK (duration_seconds BETWEEN 300 AND 21600)
);

INSERT INTO arena_control (event_id, status, duration_seconds)
SELECT id, 'OFFLINE', 2700
  FROM event
 WHERE requires_access_code;

COMMENT ON TABLE arena_control IS
    'Event-day lifecycle switch for terminal-run events. One row per event with requires_access_code.';
COMMENT ON COLUMN arena_control.status IS
    'OFFLINE (no check-in), ACTIVE (running), ENDED (over; remaining attempts finalized).';
COMMENT ON COLUMN arena_control.duration_seconds IS
    'Mission length for attempts started from now on. Running attempts keep their own expires_at.';

-- The V5 lockdown applies here too. This table decides whether the competition is
-- running; it must not be reachable through PostgREST's default grants.
ALTER TABLE arena_control ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    browser_role text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON public.arena_control FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;
