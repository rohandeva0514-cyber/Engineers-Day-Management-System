-- Three registration slots, and BuildX/Debugging opened to the second year.
--
-- WHAT CHANGED
--
-- The old rule was "one primary event, plus FIX IT", encoded as two slot values:
-- PRIMARY (at most one) and OPEN (FIX IT only). The organisers now want every
-- student to take three events, one from each of three groups:
--
--   CORE       FIX IT
--   BUILD      BuildX, Ideathon                                   - pick one
--   CHALLENGE  Chess, Debugging, Tech Debate, Rapid Research       - pick one
--
-- The underlying mechanism is unchanged and in fact simpler than before: a
-- student may hold AT MOST ONE registration per slot. PRIMARY was that rule for
-- one group; OPEN was an exemption from it. With three groups the exemption
-- disappears - FIX IT's slot simply happens to contain a single event, so "at
-- most one CORE registration" and "at most one FIX IT registration" are the same
-- sentence, and the duplicate-registration guard already covers it.
--
-- WHY THIS IS STILL A COLUMN
--
-- Same reasoning as V6. Which slot an event consumes is a property of the event,
-- not a fact about the codebase. Nothing in Java and nothing in the frontend
-- names FIX IT, BuildX or Chess; they read this column and group by it. Moving
-- Debugging into the BUILD group, or splitting CHALLENGE in two, is an UPDATE
-- here plus a value in the RegistrationSlot enum - not a branch in a service.
--
-- EXISTING REGISTRATIONS
--
-- Nothing needs repairing. The old rule was strictly tighter than the new one:
-- any pair a student legally held under "one primary plus FIX IT" is one CORE
-- registration plus one registration from either BUILD or CHALLENGE, which the
-- new rule also permits. No student can be retrospectively in violation, so
-- there is no cleanup step and no registration is cancelled by this migration.

-- 1. Re-point the slot column at the new vocabulary.
--
-- The constraint is dropped before the UPDATE and re-added after it, because the
-- new values are not members of the old CHECK.

ALTER TABLE event
    DROP CONSTRAINT ck_event_registration_slot;

ALTER TABLE event
    ALTER COLUMN registration_slot DROP DEFAULT;

UPDATE event
   SET registration_slot = CASE id
                               WHEN 'fix-it'   THEN 'CORE'
                               WHEN 'buildx'   THEN 'BUILD'
                               WHEN 'ideathon' THEN 'BUILD'
                               ELSE 'CHALLENGE'
                           END,
       updated_at        = now();

ALTER TABLE event
    ADD CONSTRAINT ck_event_registration_slot
        CHECK (registration_slot IN ('CORE', 'BUILD', 'CHALLENGE'));

-- CHALLENGE is the default for anything added later: it is the largest group and
-- the least surprising place for a new event to land, and a wrong guess is one
-- UPDATE away from being right.
ALTER TABLE event
    ALTER COLUMN registration_slot SET DEFAULT 'CHALLENGE';

COMMENT ON COLUMN event.registration_slot IS
    'Registration group. A student may hold at most one registration per slot: '
    'CORE (FIX IT), BUILD (BuildX or Ideathon), CHALLENGE (Chess, Debugging, '
    'Tech Debate or Rapid Research).';

-- 2. BuildX and Debugging are open to the second year.
--
-- Both were first-year-only in V2. Opening them is two rows plus the copy that
-- described them as first-year events; the eligibility check reads
-- event_eligible_year at request time, so nothing else has to change.

INSERT INTO event_eligible_year (event_id, year_level)
VALUES ('buildx', 2),
       ('debugging', 2)
ON CONFLICT DO NOTHING;

UPDATE event
   SET description = 'Solo build sprint, open to both years. Participants receive a theme and '
                     || 'build anything they want in their own environment. Strictly limited '
                     || 'to 30 seats.',
       updated_at  = now()
 WHERE id = 'buildx';

UPDATE event
   SET description = 'Solo debugging competition, open to both years. Race intentionally broken '
                     || 'programs in the language of your choice.',
       updated_at  = now()
 WHERE id = 'debugging';
