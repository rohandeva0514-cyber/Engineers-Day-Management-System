-- Phone, branch and division on the participant record.
--
-- All three are NULLABLE, and deliberately so. Participants created before this
-- migration have no values for them, and a NOT NULL column with an invented
-- default would either fail the migration or write fiction into rows that were
-- already correct. Requiredness belongs to the API contract for NEW submissions
-- (ParticipantRequest enforces it), not to the shape of historical rows.
--
-- The service backfills a stored participant the first time it sees these values
-- on a roster, so pre-existing records complete themselves on a student's next
-- registration rather than staying blank forever.

ALTER TABLE participant
    ADD COLUMN phone    VARCHAR(24),
    ADD COLUMN branch   VARCHAR(64),
    ADD COLUMN division VARCHAR(16);

-- Digits with optional leading +, matching the API's own shape check. Length is
-- checked here too so a bad write from any future path is refused by the
-- database rather than only by the service.
ALTER TABLE participant
    ADD CONSTRAINT ck_participant_phone
        CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{7,15}$');

COMMENT ON COLUMN participant.phone IS
    'Contact number, digits with optional leading +. Null only for records created before V3.';
COMMENT ON COLUMN participant.branch IS
    'Academic branch as selected at registration. Null only for records created before V3.';
COMMENT ON COLUMN participant.division IS
    'Class division. Null only for records created before V3.';
