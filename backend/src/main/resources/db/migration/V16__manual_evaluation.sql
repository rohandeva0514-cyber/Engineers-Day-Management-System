-- Manual evaluation: the one column the emergency judging mode actually needs.
--
-- WHY ONLY ONE COLUMN
--
-- Everything else this mode requires already exists and is reused as-is:
--
--   arena_attempt.state = 'SUBMITTED'   the final submission state (V13)
--   arena_attempt.finalized_at          the server-side submission timestamp (V13)
--   arena_attempt.score                 the participant's manual total (V13)
--   attempt_problem.draft_code          the code an organiser reviews (V14)
--
-- The only thing with nowhere to live is the points an organiser awards for one
-- problem. Adding a second timestamp or a second state would duplicate columns that
-- are already correct.
--
-- NULL MEANS "NOT YET LOOKED AT"
--
-- Deliberately distinct from 0, which means "reviewed and awarded nothing". An
-- organiser working through 30 submissions needs to tell those apart, and a default
-- of 0 would make every unreviewed problem look like a judged failure.
--
-- NO PARTIAL CREDIT, ENFORCED HERE
--
-- The rule is 0 or the full value of the difficulty. The CHECK encodes it against
-- the row's own difficulty, so a typo in an admin request cannot award 150 for an
-- EASY problem. Doing this in Java instead would leave the database willing to hold
-- a score the rules do not permit.
--
-- NOTHING IS DESTRUCTIVE
--
-- One nullable column. No existing row is read or rewritten, and no existing
-- migration is touched.

ALTER TABLE attempt_problem
    ADD COLUMN awarded_points INT;

COMMENT ON COLUMN attempt_problem.awarded_points IS
    'Points an organiser awarded by hand. NULL = not yet evaluated; 0 = evaluated, no credit.';

ALTER TABLE attempt_problem
    ADD CONSTRAINT ck_attempt_problem_awarded CHECK (
        awarded_points IS NULL
        OR awarded_points = 0
        OR (difficulty = 'EASY'   AND awarded_points = 100)
        OR (difficulty = 'MEDIUM' AND awarded_points = 200)
        OR (difficulty = 'HARD'   AND awarded_points = 300));

-- Organisers page through submissions by state; this keeps that list cheap once the
-- table holds every problem of every participant.
CREATE INDEX idx_arena_attempt_finalized ON arena_attempt (state, finalized_at);
