-- The event-day access code.
--
-- WHAT IT IS
--
-- A short, random, human-readable identifier issued when a student registers for
-- an event that is run against a terminal on the day - currently Debugging only.
-- The student saves it and presents it at the desk; the backend verifies it and
-- releases just enough detail to confirm the right person is at the machine.
--
-- WHY NOT participant.id
--
-- The participant id is a sequential BIGSERIAL. As a code presented at a desk it
-- is guessable by counting, and the endpoint that resolves it would become an
-- enumeration oracle over every registered student. A random code has neither
-- property: knowing one tells you nothing about any other.
--
-- WHY IT LIVES ON registration, NOT participant
--
-- The code authorises a person for ONE event. Putting it on the participant would
-- make it a second student identity - competing with email, which V7 established
-- as the only one - and would say nothing about which event it admits someone to.
-- On the registration row it is unambiguous: this code, this student, this event.
--
-- WHICH EVENTS ISSUE ONE IS DATA
--
-- `event.requires_access_code` rather than a check for 'debugging' in Java. Same
-- reasoning as registration_slot in V6: every other per-event rule here is a
-- column the services read, which is why Tech Debate could become solo in V4
-- without touching code. Giving a second event terminal check-in later is one
-- UPDATE.
--
-- NOTHING IS DESTRUCTIVE
--
-- The column is nullable and every existing row keeps NULL. Registrations taken
-- before this migration simply have no code, which is correct: they were never
-- issued one.

ALTER TABLE event
    ADD COLUMN requires_access_code BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE event
   SET requires_access_code = TRUE,
       updated_at           = now()
 WHERE id = 'debugging';

ALTER TABLE registration
    ADD COLUMN access_code VARCHAR(16);

-- Unique where present. A partial index rather than a plain UNIQUE so the many
-- NULLs from events that issue no code cost nothing and collide with nothing.
-- This is also what makes the generator's retry-on-collision correct: the
-- database, not the application, decides that a code is already taken.
CREATE UNIQUE INDEX uq_registration_access_code
    ON registration (access_code)
 WHERE access_code IS NOT NULL;

-- Shape guard. The generator emits 8 characters from an unambiguous alphabet;
-- this stops any other write path storing something that cannot be read off a
-- phone screen and typed at a desk.
ALTER TABLE registration
    ADD CONSTRAINT ck_registration_access_code
        CHECK (access_code IS NULL OR access_code ~ '^[2-9A-HJ-NP-Z]{8}$');

COMMENT ON COLUMN registration.access_code IS
    'Event-day access code. Random, unique, issued only for events with requires_access_code.';
COMMENT ON COLUMN event.requires_access_code IS
    'True when registering for this event issues an event-day access code.';
