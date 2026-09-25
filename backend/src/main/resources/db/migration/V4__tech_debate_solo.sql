-- Tech Debate registers as individuals, for now.
--
-- TEMPORARY. Tech Debate is a ten-person team event and is expected to return to
-- that; the organisers are collecting individual entries first and forming teams
-- themselves.
--
-- This is a DATA change on purpose. Participation type and roster bounds are
-- columns the services read at request time — nothing in Java, and nothing in the
-- frontend, names Tech Debate or branches on it. Restoring the team event is
-- therefore one migration:
--
--     UPDATE event SET participation_type = 'TEAM', min_team_size = 10,
--                      max_team_size = 10
--      WHERE id = 'tech-debate';
--
-- No code, no redeploy of the registration logic.
--
-- Registrations already taken under the team rules are untouched: existing rows
-- keep their team_id, and team membership is not re-validated after the fact.

UPDATE event
   SET participation_type = 'SOLO',
       min_team_size      = 1,
       max_team_size      = 1,
       description        = 'Debate on contemporary technology motions. Entering individually '
                            || 'for now - the organisers form the teams.'
 WHERE id = 'tech-debate';
