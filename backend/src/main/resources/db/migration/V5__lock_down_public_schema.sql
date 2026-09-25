-- Deny-all lockdown of the public schema.
--
-- WHY THIS EXISTS
--
-- This application never touches PostgREST. The backend connects over JDBC as the
-- owning role and is the only thing that reads or writes these tables; the browser
-- talks to /api and has no database client at all.
--
-- But a Supabase project exposes every table in `public` through PostgREST, and
-- grants the `anon` and `authenticated` roles default privileges on tables created
-- there. Tables created by Flyway are raw SQL, not dashboard tables, so row level
-- security is NOT enabled on them automatically. Left alone, anyone holding the
-- project URL and the anon key - both public by design - could read every
-- participant's name, email and phone number, and write rows of their own.
--
-- So this lockdown is not for the application's benefit. It closes a door that
-- Supabase opens by default and that nothing here ever intends to use.
--
-- NO POLICIES ARE CREATED, DELIBERATELY
--
-- Row level security with zero policies denies everything to every non-owning
-- role. That is exactly right here: there is no browser-side caller that should
-- ever be let through, so there is no rule to write. The backend is unaffected,
-- because a table's owner bypasses RLS unless FORCE ROW LEVEL SECURITY is set,
-- which it is not.
--
-- NOTHING IS DESTRUCTIVE
--
-- No row is read, written or deleted. This changes only privileges and flags, and
-- is safe to apply to a database that already holds registrations.
--
-- WHY flyway_schema_history IS NOT TOUCHED HERE
--
-- Flyway holds a lock on its own history table for the duration of a migration
-- run. Both ALTER TABLE ... ENABLE ROW LEVEL SECURITY and REVOKE need an ACCESS
-- EXCLUSIVE lock on their target, so a migration that names that table waits on a
-- lock Flyway will not release until the migration finishes - a deadlock with
-- itself that hangs the application on startup rather than failing it.
--
-- It is therefore excluded, and every statement below names its tables explicitly
-- instead of using ALL TABLES IN SCHEMA public, which would sweep it back in. The
-- history table leaks only migration descriptions, no participant data. To lock it
-- down too, run this ONCE by hand, outside Flyway:
--
--     ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
--     REVOKE ALL ON public.flyway_schema_history FROM anon, authenticated;
--
-- PORTABILITY
--
-- `anon` and `authenticated` are Supabase roles. They do not exist in a plain
-- PostgreSQL instance such as the local docker-compose database, where REVOKE
-- against a missing role is a hard error that would fail the whole migration. The
-- revokes are therefore guarded on the role actually existing, so this file
-- applies cleanly to both.

-- ---------------------------------------------------------------------------
-- 1. Row level security, deny-all, on every application table.
-- ---------------------------------------------------------------------------
-- Listed explicitly rather than looped over the catalogue: a future migration that
-- adds a table must make a deliberate decision about it, and silently inheriting
-- this lockdown would hide that decision.

ALTER TABLE public.participant         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.event               ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.event_eligible_year ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.registration        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_member         ENABLE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------
-- 2. Remove the grants Supabase hands to browser-facing roles.
-- ---------------------------------------------------------------------------
-- RLS alone already denies reads and writes, but leaving the grants in place means
-- the tables still show up in the generated API surface. Revoking states the intent
-- plainly: these roles have no business here at all.
--
-- Sequences use ALL ... IN SCHEMA public because no sequence belongs to
-- flyway_schema_history, so there is no lock to collide with, and any sequence a
-- later migration adds is then covered without needing to be listed.

DO $$
DECLARE
    browser_role text;
    app_table    text;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            -- Expected on plain PostgreSQL. Not a problem: the role that would hold
            -- the privileges does not exist, so there is nothing to take away.
            RAISE NOTICE 'Role % not present; nothing to revoke', browser_role;
            CONTINUE;
        END IF;

        FOREACH app_table IN ARRAY ARRAY[
            'participant', 'event', 'event_eligible_year',
            'registration', 'team', 'team_member'
        ] LOOP
            EXECUTE format('REVOKE ALL ON public.%I FROM %I', app_table, browser_role);
        END LOOP;

        EXECUTE format(
            'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', browser_role);

        RAISE NOTICE 'Revoked public schema privileges from %', browser_role;
    END LOOP;
END
$$;
