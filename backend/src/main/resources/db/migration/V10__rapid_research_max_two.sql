-- Rapid Research teams are capped at two members.
--
-- Was 1-4 (V2__seed_events.sql). The organisers want pairs at most: the
-- preparation window is short and a paper written under the clock does not
-- divide well across four people.
--
-- This is a DATA change on purpose. Roster bounds are columns the services read
-- at request time - nothing in Java, and nothing in the frontend, names Rapid
-- Research or branches on it. Widening it again is one UPDATE.
--
-- Registrations already taken under the 1-4 rule are untouched: existing teams
-- keep their members, and team membership is not re-validated after the fact.
-- Any three- or four-person team already registered stays registered, and the
-- organisers resolve it off-platform.

UPDATE event
   SET max_team_size = 2,
       description   = 'Second year exclusive. Teams of up to two receive a problem statement, '
                       || 'get a limited preparation window, write a research paper and submit '
                       || 'it for evaluation.'
 WHERE id = 'rapid-research';
