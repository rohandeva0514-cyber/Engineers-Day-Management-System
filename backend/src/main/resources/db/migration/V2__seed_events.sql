-- Seed the seven Engineers' Day 2026 events.
--
-- Every rule here comes from CLAUDE.md. Nothing in this file is duplicated in Java:
-- the services read these rows and decide from them, so changing an event's team size
-- or capacity is a migration, not a code change.
--
-- "Exactly 10" for Tech Debate is expressed as min_team_size = max_team_size = 10.
-- A SOLO event is min = max = 1, so the roster validator has one code path.

INSERT INTO event (id, name, description, participation_type,
                   min_team_size, max_team_size, capacity, capacity_unit,
                   registration_status, display_order)
VALUES
  ('chess', 'Chess',
   'Classical over-the-board chess. Solo entry, open to first and second year students.',
   'SOLO', 1, 1, NULL, 'PARTICIPANT', 'REGISTRATION_OPEN', 10),

  ('tech-debate', 'Tech Debate',
   'Team debate on contemporary technology motions. Teams must contain exactly 10 members.',
   'TEAM', 10, 10, NULL, 'TEAM', 'REGISTRATION_OPEN', 20),

  ('fix-it', 'FIX IT',
   'Teams receive a struggling business situation - falling sales, weak marketing, poor retention - '
   'analyse it, identify the core problems, build a turnaround strategy and present it to judges.',
   'TEAM', 1, 4, NULL, 'TEAM', 'REGISTRATION_OPEN', 30),

  ('ideathon', 'Ideathon',
   'Teams of up to four take an idea from problem statement to pitch. Limited capacity.',
   'TEAM', 1, 4, 40, 'TEAM', 'REGISTRATION_OPEN', 40),

  ('buildx', 'BuildX',
   'First year solo build sprint. Participants receive a theme and build anything they want in '
   'their own environment. Strictly limited to 30 seats.',
   'SOLO', 1, 1, 30, 'PARTICIPANT', 'REGISTRATION_OPEN', 50),

  ('debugging', 'Debugging',
   'First year solo debugging competition against intentionally broken programs in the language '
   'of your choice.',
   'SOLO', 1, 1, NULL, 'PARTICIPANT', 'REGISTRATION_OPEN', 60),

  ('rapid-research', 'Rapid Research',
   'Second year exclusive. Teams receive a problem statement, get a limited preparation window, '
   'write a research paper and submit it for evaluation.',
   'TEAM', 1, 4, NULL, 'TEAM', 'REGISTRATION_OPEN', 70);

-- Eligibility, straight from CLAUDE.md:
--   Chess, Tech Debate, FIX IT, Ideathon  -> years 1 and 2
--   BuildX, Debugging                     -> year 1 only
--   Rapid Research                        -> year 2 only
INSERT INTO event_eligible_year (event_id, year_level)
VALUES
  ('chess', 1),          ('chess', 2),
  ('tech-debate', 1),    ('tech-debate', 2),
  ('fix-it', 1),         ('fix-it', 2),
  ('ideathon', 1),       ('ideathon', 2),
  ('buildx', 1),
  ('debugging', 1),
  ('rapid-research', 2);
