-- Engineers' Day 2026 - registration schema.
--
-- Design notes:
--  * Business rules that CAN be expressed as constraints ARE expressed as constraints.
--    The application is not the last line of defence; the database is.
--  * event.seats_taken + the capacity CHECK make overselling unrepresentable, so the
--    conditional UPDATE in EventRepository.tryClaimSeats cannot be defeated by a race.
--  * UNIQUE (event_id, participant_id) on registration is what makes duplicate
--    registration impossible even under concurrent requests.

-- ---------------------------------------------------------------------------
-- participant
-- ---------------------------------------------------------------------------
CREATE TABLE participant (
    id          BIGSERIAL     PRIMARY KEY,
    roll_no     VARCHAR(32)   NOT NULL,
    full_name   VARCHAR(120)  NOT NULL,
    email       VARCHAR(160)  NOT NULL,
    year_level  SMALLINT      NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Roll numbers are normalised to upper case by the service; the constraint stops
    -- any future write path from quietly reintroducing case-variant duplicates.
    CONSTRAINT ck_participant_roll_upper CHECK (roll_no = upper(roll_no)),
    CONSTRAINT ck_participant_year       CHECK (year_level IN (1, 2))
);

-- Roll number and email are the two identities a student is known by. Both are
-- unique, case-insensitively, so "1ms24cs001" cannot shadow "1MS24CS001".
CREATE UNIQUE INDEX uq_participant_roll  ON participant (roll_no);
CREATE UNIQUE INDEX uq_participant_email ON participant (lower(email));

-- ---------------------------------------------------------------------------
-- event
-- ---------------------------------------------------------------------------
-- id is a human-readable slug ('buildx', 'tech-debate'): it is stable, it is the
-- public API identifier, and it makes every FK legible in a psql session on event day.
CREATE TABLE event (
    id                  VARCHAR(32)   PRIMARY KEY,
    name                VARCHAR(80)   NOT NULL,
    description         TEXT          NOT NULL,
    participation_type  VARCHAR(16)   NOT NULL,
    min_team_size       INT           NOT NULL,
    max_team_size       INT           NOT NULL,
    capacity            INT,                       -- NULL => no automatic limit (manual closure)
    capacity_unit       VARCHAR(16)   NOT NULL,    -- what one seat counts: PARTICIPANT or TEAM
    seats_taken         INT           NOT NULL DEFAULT 0,
    registration_status VARCHAR(24)   NOT NULL,
    display_order       INT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_event_participation CHECK (participation_type IN ('SOLO', 'TEAM')),
    CONSTRAINT ck_event_capacity_unit CHECK (capacity_unit IN ('PARTICIPANT', 'TEAM')),
    CONSTRAINT ck_event_reg_status    CHECK (registration_status IN
                                             ('REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'SOLD_OUT')),
    CONSTRAINT ck_event_team_size     CHECK (min_team_size >= 1 AND max_team_size >= min_team_size),
    -- A SOLO event is exactly a team of one. Encoding it this way means the roster
    -- validator has a single code path instead of a special case.
    CONSTRAINT ck_event_solo_sizes    CHECK (participation_type <> 'SOLO'
                                             OR (min_team_size = 1 AND max_team_size = 1)),
    CONSTRAINT ck_event_capacity      CHECK (capacity IS NULL OR capacity >= 0),
    -- THE capacity invariant. Overselling is now unrepresentable.
    CONSTRAINT ck_event_seats_taken   CHECK (seats_taken >= 0
                                             AND (capacity IS NULL OR seats_taken <= capacity))
);

-- Eligible years are a child table, not a bitmask or a CSV column: it keeps the
-- "is this student eligible" query a join, and adding a third year is a data change.
CREATE TABLE event_eligible_year (
    event_id   VARCHAR(32) NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    year_level SMALLINT    NOT NULL,

    PRIMARY KEY (event_id, year_level),
    CONSTRAINT ck_eligible_year CHECK (year_level IN (1, 2))
);

-- ---------------------------------------------------------------------------
-- team
-- ---------------------------------------------------------------------------
CREATE TABLE team (
    id                     BIGSERIAL    PRIMARY KEY,
    event_id               VARCHAR(32)  NOT NULL REFERENCES event (id),
    name                   VARCHAR(120) NOT NULL,
    captain_participant_id BIGINT       NOT NULL REFERENCES participant (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_team_event_name ON team (event_id, lower(name));
CREATE INDEX idx_team_event ON team (event_id);

CREATE TABLE team_member (
    team_id        BIGINT      NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    participant_id BIGINT      NOT NULL REFERENCES participant (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (team_id, participant_id)
);

CREATE INDEX idx_team_member_participant ON team_member (participant_id);

-- ---------------------------------------------------------------------------
-- registration
-- ---------------------------------------------------------------------------
-- One row per participant per event, whether they registered solo or as part of a
-- team. A team of ten produces ten registration rows sharing one team_id, so
-- "is this person registered for this event" is one indexed lookup regardless.
CREATE TABLE registration (
    id              BIGSERIAL    PRIMARY KEY,
    event_id        VARCHAR(32)  NOT NULL REFERENCES event (id),
    participant_id  BIGINT       NOT NULL REFERENCES participant (id),
    team_id         BIGINT       REFERENCES team (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(80),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Duplicate registration is impossible, not merely discouraged.
    CONSTRAINT uq_registration_event_participant UNIQUE (event_id, participant_id)
);

CREATE INDEX idx_registration_participant ON registration (participant_id);
CREATE INDEX idx_registration_event       ON registration (event_id);
CREATE INDEX idx_registration_team        ON registration (team_id);

-- Retrying a POST that already succeeded must not create a second registration.
CREATE UNIQUE INDEX uq_registration_idempotency
    ON registration (event_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
