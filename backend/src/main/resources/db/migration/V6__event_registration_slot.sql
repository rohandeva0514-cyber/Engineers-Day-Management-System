-- The registration slot an event consumes.
--
-- The rule being encoded: a student may hold ONE primary event, plus FIX IT. Two
-- primary events is never allowed; FIX IT alongside a primary event always is.
--
-- WHY THIS IS A COLUMN AND NOT AN `if (eventId.equals("fix-it"))`
--
-- Every other event rule in this system is data - participation type, team bounds,
-- capacity, eligible years - and the services read them at request time. That is
-- what keeps the Java free of per-event branching, and it is why Tech Debate could
-- become a solo event in V4 without a line of code changing.
--
-- The same applies here. "FIX IT does not consume the primary slot" is a property
-- of an event, not a fact about the codebase. Storing it as a column means:
--
--   * nothing in Java, and nothing in the frontend, names FIX IT;
--   * a second open event later is one UPDATE, not a new branch;
--   * reverting FIX IT to a normal event is one UPDATE too.
--
-- PRIMARY  consumes the single primary slot. Holding one blocks every other
--          PRIMARY event.
-- OPEN     consumes no primary slot. Can be held alongside a primary event, and
--          alongside nothing at all.
--
-- The default is PRIMARY, so an event added later is restrictive until someone
-- deliberately opens it up. The duplicate guard is unchanged: UNIQUE
-- (event_id, participant_id) still stops the same person registering twice for
-- the same event, whichever slot it uses.

ALTER TABLE event
    ADD COLUMN registration_slot VARCHAR(16) NOT NULL DEFAULT 'PRIMARY';

ALTER TABLE event
    ADD CONSTRAINT ck_event_registration_slot
        CHECK (registration_slot IN ('PRIMARY', 'OPEN'));

-- FIX IT is the open event. Chess, Tech Debate, Ideathon, BuildX, Debugging and
-- Rapid Research keep the PRIMARY default.
UPDATE event
   SET registration_slot = 'OPEN',
       updated_at        = now()
 WHERE id = 'fix-it';

COMMENT ON COLUMN event.registration_slot IS
    'PRIMARY consumes the student''s single primary-event slot; OPEN can be held alongside it.';
