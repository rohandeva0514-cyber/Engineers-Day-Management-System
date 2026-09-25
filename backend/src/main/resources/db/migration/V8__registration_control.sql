-- Admin registration control: Debugging's seat limit, and a global switch.
--
-- DEBUGGING GETS THE CAPACITY IT ALWAYS NEEDED
--
-- BuildX already carries capacity = 30 from V2. Debugging was seeded with NULL,
-- meaning "no automatic limit, closed by hand". It has the same 30-seat rule, so
-- it gets the same mechanism rather than a second one.
--
-- Setting the column is all that is required. The atomic claim in
-- EventRepository.tryClaimSeats is:
--
--     UPDATE event SET seats_taken = seats_taken + :seats
--      WHERE id = :eventId
--        AND (capacity IS NULL OR seats_taken + :seats <= capacity)
--
-- The WHERE clause is evaluated under the row lock the UPDATE itself takes, so
-- concurrent claims serialise: exactly `capacity` callers get a row count of 1
-- and every other caller gets 0. There is no SELECT-then-INSERT window, which is
-- why the 31st registration cannot slip through no matter how many arrive at
-- once. ck_event_seats_taken (V1) is the backstop underneath it.
--
-- Guarded so the migration cannot fail on a database that has somehow already
-- taken more than 30: it would violate ck_event_seats_taken and abort the deploy.
UPDATE event
   SET capacity      = 30,
       capacity_unit = 'PARTICIPANT',
       updated_at    = now()
 WHERE id = 'debugging'
   AND seats_taken <= 30;

-- ---------------------------------------------------------------------------
-- Global registration switch
-- ---------------------------------------------------------------------------
-- One row, forever. The CHECK on a boolean primary key fixed to TRUE is what
-- makes that structural rather than a convention someone later breaks: a second
-- row cannot be inserted, so "the settings" is never ambiguous.
--
-- A table rather than a config property because this is operational state an
-- organiser flips during the event, not deployment configuration. It has to
-- survive a restart and be visible to every instance at once.
--
-- Deliberately NOT duplicating per-event state. Events keep their own
-- registration_status; this is the master switch above them, and the service
-- checks it first.

CREATE TABLE system_setting (
    id                 BOOLEAN     PRIMARY KEY DEFAULT TRUE,
    registrations_open BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_system_setting_singleton CHECK (id)
);

INSERT INTO system_setting (id, registrations_open) VALUES (TRUE, TRUE);

COMMENT ON TABLE system_setting IS
    'Single-row operational state. The singleton CHECK makes a second row impossible.';
COMMENT ON COLUMN system_setting.registrations_open IS
    'Master switch. When false every registration is refused regardless of per-event status.';

-- The lockdown from V5 applies to this table too: it is reachable through
-- PostgREST otherwise, and it controls whether the whole event accepts entries.
ALTER TABLE system_setting ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    browser_role text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON public.system_setting FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;
