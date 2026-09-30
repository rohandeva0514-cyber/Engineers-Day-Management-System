-- Execution history, and the per-problem submission lock.
--
-- WHAT IS RECORDED, AND WHAT IS NOT
--
-- One row per execution the participant asked for. It records the SHAPE of the
-- result - the verdict, how many visible tests passed, how long it took - and not
-- the content of anything. No source code, no test inputs, no expected outputs, no
-- participant stdout.
--
-- The source is deliberately absent: attempt_problem.draft_code already holds the
-- participant's current code, and copying it per run would turn an audit log into
-- a second, larger copy of everyone's work for no benefit anybody has asked for.
--
-- Hidden test CONTENT is never written here, and never will be. The counts from a
-- submission are, because an organiser adjudicating a dispute needs to know how a
-- verdict was reached - and this table is admin-only and is never projected into a
-- participant response. Decision 5 governs what leaves the server, not what the
-- server records for itself.
--
-- WHY kind IS A COLUMN
--
-- A RUN and a SUBMIT are the same machinery pointed at different test sets, so
-- they share a table. Separating them would duplicate every column to record a
-- single boolean's worth of difference.
--
-- THE SUBMISSION LOCK
--
-- attempt_problem.submitted_at is what makes a per-problem submission final. It is
-- set once, never cleared, and every write path checks it. Note it is separate from
-- status: a submission that fails its hidden tests still locks the problem, and
-- conflating "locked" with "solved" would either let a failed submission be retried
-- or mark a wrong answer solved.
--
-- NOTHING IS DESTRUCTIVE
--
-- One new table and one nullable column. No existing row is read or rewritten.

ALTER TABLE attempt_problem
    ADD COLUMN submitted_at TIMESTAMPTZ;

COMMENT ON COLUMN attempt_problem.submitted_at IS
    'Set once when the participant submits this problem. Locks it, whether or not it solved.';

-- A problem cannot be solved without having been submitted: SOLVED is only ever
-- reached through the hidden-test path, and that path always stamps submitted_at.
-- This makes "solved but never submitted" unrepresentable rather than merely
-- unlikely.
ALTER TABLE attempt_problem
    ADD CONSTRAINT ck_attempt_problem_submit_order
        CHECK (status <> 'SOLVED' OR submitted_at IS NOT NULL);

CREATE TABLE arena_run (
    id                 BIGSERIAL    PRIMARY KEY,

    attempt_problem_id BIGINT       NOT NULL
                                    REFERENCES attempt_problem (id) ON DELETE CASCADE,

    -- RUN executes the visible tests; SUBMIT executes the hidden ones.
    kind               VARCHAR(8)   NOT NULL,

    -- The normalised verdict the participant was shown. Judge0's own status ids
    -- are deliberately not stored: they are an implementation detail of one engine,
    -- and this column has to stay meaningful if the engine is ever replaced.
    verdict            VARCHAR(24)  NOT NULL,

    tests_passed       INT          NOT NULL DEFAULT 0,
    tests_total        INT          NOT NULL DEFAULT 0,

    -- Wall time for the whole batch, as measured by the backend. Judge0's own
    -- per-submission timings are not persisted; they are infrastructure telemetry.
    duration_ms        INT,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_arena_run_kind CHECK (kind IN ('RUN', 'SUBMIT')),

    CONSTRAINT ck_arena_run_verdict CHECK (verdict IN
        ('ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
         'TIME_LIMIT_EXCEEDED', 'INTERNAL_ERROR')),

    CONSTRAINT ck_arena_run_counts CHECK (
        tests_passed >= 0 AND tests_total >= 0 AND tests_passed <= tests_total)
);

CREATE INDEX idx_arena_run_attempt_problem ON arena_run (attempt_problem_id);

COMMENT ON TABLE arena_run IS
    'One execution. Records the shape of a result - never source, test content or participant output.';
COMMENT ON COLUMN arena_run.verdict IS
    'Engine-independent verdict. Judge0 status ids are never stored.';

-- The V5 lockdown.
ALTER TABLE arena_run ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    browser_role text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON public.arena_run FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.arena_run_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END
$$;
