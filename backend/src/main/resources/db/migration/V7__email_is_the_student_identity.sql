-- Email is the student identity. Roll number is an attribute.
--
-- WHAT WAS WRONG
--
-- V1 made roll_no globally unique and the services resolved a student by it. Two
-- real students who share a roll number - different branches, different years,
-- re-used numbering - could therefore not both register: the second was refused
-- as an identity conflict with the first.
--
-- Email is the identifier that is actually unique per student, and it is the one
-- a student can be contacted on. So it becomes the key, and roll_no becomes what
-- it always was: a field on the record.
--
-- WHAT CHANGES
--
--   * uq_participant_roll is dropped. Two students may now share a roll number.
--   * A NON-unique index replaces it, because roll_no is still looked up on
--     event day and a sequential scan of every participant is not the way to do
--     it.
--   * uq_participant_email is UNTOUCHED and still case-insensitive. This is the
--     constraint the whole identity model now rests on, so it is asserted below
--     rather than assumed.
--
-- CONSEQUENCE FOR THE REGISTRATION RULE
--
-- "One primary event plus FIX IT" is enforced against the participant row, which
-- is now reached by email. Submitting a different roll number with the same email
-- resolves to the same student and is refused exactly as before - changing the
-- roll number cannot buy a second primary event.
--
-- NOTHING IS DESTRUCTIVE
--
-- No row is read, written or deleted. Dropping a unique index only ever widens
-- what is accepted, so every existing participant remains valid.

DROP INDEX IF EXISTS uq_participant_roll;

-- Kept as a plain index: still the fastest way to find someone at the desk, with
-- no claim that the answer is unique.
CREATE INDEX IF NOT EXISTS idx_participant_roll ON participant (roll_no);

-- The identity constraint. Asserted, not assumed: if a future migration ever
-- removes it, this fails loudly at deploy time rather than silently allowing two
-- participant records for one student.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'public'
           AND tablename  = 'participant'
           AND indexname  = 'uq_participant_email'
    ) THEN
        RAISE EXCEPTION
            'uq_participant_email is missing. Email must stay unique - it is the '
            'student identity that registration limits are enforced against.';
    END IF;
END
$$;

COMMENT ON COLUMN participant.roll_no IS
    'College roll number. NOT unique - two students may share one. An attribute, not the identity.';
COMMENT ON COLUMN participant.email IS
    'The student identity. Unique case-insensitively; registration limits are enforced against it.';
