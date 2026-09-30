-- Per-problem state for one participant's attempt.
--
-- STILL NO PROBLEM BANK IN POSTGRESQL
--
-- This table holds what the PARTICIPANT did - status, draft code, revision - and
-- never the problem itself. The statement, the buggy code, the corrected code, the
-- bug notes and the hidden tests all stay in the five JSON resources, which is what
-- keeps answer material out of the database entirely. A dump of this table shows
-- what a student typed and nothing they were supposed to work out.
--
-- problem_id is therefore a plain string, not a foreign key: it is the bank's own
-- identifier ('C++-E-001'), and there is no table for it to reference.
--
-- WHY ref EXISTS ALONGSIDE problem_id
--
-- Bank ids carry the language and are not URL-safe - 'C++-E-001' contains two '+'
-- characters, which some proxies rewrite as spaces inside a path segment. `ref` is
-- the short, stable, URL-safe handle the API uses instead: E-01 .. E-04, M-01 ..
-- M-04, H-01 .. H-04.
--
-- It is only unique WITHIN an attempt, which is all that is needed: an attempt has
-- exactly one language, so a request naming E-02 is unambiguous. It also means the
-- API never has to echo a bank id back to a participant, so the id space stays an
-- internal detail.
--
-- TWO UNIQUE CONSTRAINTS, NOT ONE
--
-- (attempt_id, ref) stops two rows claiming the same slot on the board.
-- (attempt_id, problem_id) stops the same bank problem being materialised twice
-- under different refs. Materialisation is deterministic, so neither should ever
-- fire - which is exactly why they are cheap to keep and worth having.
--
-- SOLVED IS NOT REACHABLE YET
--
-- The status CHECK permits it because the column's domain is settled, but nothing
-- in Phase C can set it: marking a problem solved requires running hidden tests,
-- and that is Phase D. Saving a draft moves NOT_ATTEMPTED -> ATTEMPTED and no
-- further. ck_attempt_problem_solved ties solved_at to the status so the two can
-- never disagree once Phase D does start writing it.
--
-- NOTHING IS DESTRUCTIVE
--
-- A new table. No existing row is read, written or deleted.

CREATE TABLE attempt_problem (
    id             BIGSERIAL    PRIMARY KEY,

    attempt_id     BIGINT       NOT NULL REFERENCES arena_attempt (id) ON DELETE CASCADE,

    -- The bank's own id, e.g. 'C++-E-001'. Traceability only; never sent to a
    -- participant and never used to address an endpoint.
    problem_id     VARCHAR(32)  NOT NULL,

    -- URL-safe board handle, e.g. 'E-01'.
    ref            VARCHAR(8)   NOT NULL,

    difficulty     VARCHAR(8)   NOT NULL,

    -- Position within its difficulty group, 1-4. Board ordering is taken from this
    -- rather than from the bank's file order, so the board cannot reshuffle between
    -- requests.
    ordinal        INT          NOT NULL,

    status         VARCHAR(16)  NOT NULL DEFAULT 'NOT_ATTEMPTED',

    -- What the participant has typed. NULL means untouched - the client falls back
    -- to the bank's buggy_code, so an untouched problem costs no storage.
    draft_code     TEXT,

    -- Bumped on every accepted save. The client sends the revision it last saw, so
    -- a stale tab cannot silently overwrite work done on another device.
    draft_revision INT          NOT NULL DEFAULT 0,

    -- Phase D/E fill these.
    run_count      INT          NOT NULL DEFAULT 0,
    solved_at      TIMESTAMPTZ,
    final_passed   INT,
    final_total    INT,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_attempt_problem_status CHECK (status IN
        ('NOT_ATTEMPTED', 'ATTEMPTED', 'SOLVED')),

    CONSTRAINT ck_attempt_problem_difficulty CHECK (difficulty IN
        ('EASY', 'MEDIUM', 'HARD')),

    CONSTRAINT ck_attempt_problem_ordinal CHECK (ordinal BETWEEN 1 AND 4),

    CONSTRAINT ck_attempt_problem_revision CHECK (draft_revision >= 0),

    CONSTRAINT ck_attempt_problem_runs CHECK (run_count >= 0),

    -- solved_at is set exactly when the status is SOLVED. Neither can drift.
    CONSTRAINT ck_attempt_problem_solved CHECK (
        (status = 'SOLVED' AND solved_at IS NOT NULL)
        OR (status <> 'SOLVED' AND solved_at IS NULL)),

    CONSTRAINT uq_attempt_problem_ref  UNIQUE (attempt_id, ref),
    CONSTRAINT uq_attempt_problem_bank UNIQUE (attempt_id, problem_id)
);

-- The board is fetched on every navigation, always scoped to one attempt.
CREATE INDEX idx_attempt_problem_attempt ON attempt_problem (attempt_id);

COMMENT ON TABLE attempt_problem IS
    'What a participant did on one problem. Never the problem itself - the bank stays in JSON resources.';
COMMENT ON COLUMN attempt_problem.problem_id IS
    'The bank''s own id, e.g. C++-E-001. Not a foreign key: there is no problem table.';
COMMENT ON COLUMN attempt_problem.ref IS
    'URL-safe board handle (E-01..H-04), unique within an attempt. What the API addresses.';
COMMENT ON COLUMN attempt_problem.draft_code IS
    'Participant''s working code. NULL means untouched; the client falls back to the bank''s buggy_code.';

-- The V5 lockdown. This table holds every participant's working code.
ALTER TABLE attempt_problem ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    browser_role text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON public.attempt_problem FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.attempt_problem_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;
