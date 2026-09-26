# Engineers' Day 2026 — Registration Service

Server-authoritative registration for MIT TECH KERNEL's Engineers' Day 2026.

This milestone covers **event discovery and registration only**. There is deliberately no
authentication, no event execution, no submissions, no judging, no results, no admin UI and
no realtime layer — those are later milestones.

The frontend is never trusted. Eligibility, team size, capacity, duplicate detection and the
clock are all decided here, against the database.

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 21+ | Compiled with `--release 21`; runs on newer JDKs. |
| Maven | 3.9+ | Or use the bundled `./mvnw`. |
| Docker | 20.10+ | For the local database, and for the test suite (Testcontainers). |

---

## 1. Run PostgreSQL

```bash
cd backend
docker compose up -d
```

This starts `postgres:16-alpine` on host port **55432** with database, user and password all
`engineers_day`, and a named volume so data survives a restart.

> **Why 55432 and not 5432?** Many machines already run a PostgreSQL service on 5432 — this
> one does (`postgresql-x64-18`). Docker will happily publish alongside it, and the
> application then connects to whichever wins the race, which surfaces as a baffling
> `password authentication failed`. A distinct port removes the ambiguity. Override with
> `DB_PORT` if you prefer.

```bash
docker compose ps                 # check health
docker compose logs -f postgres   # follow logs
docker compose down               # stop, keep data
docker compose down -v            # stop and wipe (Flyway rebuilds on next boot)
```

Already running Postgres elsewhere? Skip compose and point `DB_URL` at it. The service only
needs a database it can create tables in and a role that owns it.

---

## 2. Configure environment variables

No credentials are committed. `application.yml` reads everything from the environment.

```bash
cp .env.example .env
```

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:55432/engineers_day` | JDBC URL |
| `DB_USERNAME` | `engineers_day` | Database user |
| `DB_PASSWORD` | *(empty)* | Database password — set it to `engineers_day` for the compose database |
| `DB_POOL_SIZE` | `10` | HikariCP maximum pool size |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated. No wildcard. |
| `LOG_LEVEL` | `INFO` | Level for `in.mittechkernel.registration` |
| `SHOW_SQL` | `false` | Log generated SQL |

The defaults exist so a fresh clone boots against the compose database with no setup. They
are not production configuration — set real values in the deployment environment.

Spring Boot does not read `.env` by itself. Either export it:

```bash
set -a; source .env; set +a        # bash / zsh
```

```powershell
Get-Content .env | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } |
  ForEach-Object { $k,$v = $_ -split '=',2; [Environment]::SetEnvironmentVariable($k,$v) }
```

…or set the variables in your IDE's run configuration.

---

## 3. Start the service

```bash
./mvnw spring-boot:run           # macOS / Linux
mvnw.cmd spring-boot:run         # Windows
```

Or build a jar and run it:

```bash
./mvnw clean package
java -jar target/registration-service-0.1.0-SNAPSHOT.jar
```

Check it is up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

`health` reports `DOWN` if the database is unreachable, so a green check means the whole
path is working, not just that the process started.

---

## 4. Database migrations

**Flyway owns the schema.** Hibernate runs with `ddl-auto: validate` and will never create,
alter or drop anything. This is not a stylistic preference: the CHECK constraints in
`V1` are what make overselling impossible, and a Hibernate-generated schema would silently
omit them.

| Migration | Contents |
|---|---|
| `V1__registration_schema.sql` | Tables, keys, indexes, constraints |
| `V2__seed_events.sql` | The seven events and their eligibility rules |

Migrations run automatically at startup, in order, once each. To add one, create
`V3__description.sql` in `src/main/resources/db/migration` — **never edit a migration that
has already been applied**, because Flyway checksums them and will refuse to start.

Inspect state:

```bash
docker compose exec postgres psql -U engineers_day -d engineers_day \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Start over locally:

```bash
docker compose down -v && docker compose up -d
```

---

## 5. Endpoints

Base path `/api`. All responses are JSON.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/events` | All seven events, in display order |
| `GET` | `/api/events/{eventId}` | One event. Id is a slug and case-insensitive |
| `POST` | `/api/registrations` | Register one participant or one complete team |
| `GET` | `/api/registrations/{participantId}` | Everything a participant is registered for |
| `GET` | `/api/registrations?rollNo={rollNo}` | The same, found by roll number |
| `GET` | `/actuator/health` | Liveness, including the database |

Event ids: `chess`, `tech-debate`, `fix-it`, `ideathon`, `buildx`, `debugging`,
`rapid-research`.

### Why registration is one endpoint

Creating a team, adding its members and registering it happen in a **single atomic POST**
rather than three calls. A multi-step flow leaves half-built teams behind every time someone
closes a tab, and with Tech Debate requiring exactly ten members that would be the common
case rather than the edge case. Submitting the whole roster at once means a team either
exists complete and valid or does not exist at all — which is also what makes the seat claim
honest, since a team's seats are taken in the same transaction that creates it.

The first entry in `participants` is the captain.

---

## 6. Sample requests

### List events

```bash
curl -s http://localhost:8080/api/events | jq '.[] | {eventId, participationType, capacity}'
```

```json
{
  "eventId": "buildx",
  "name": "BuildX",
  "description": "First year solo build sprint...",
  "eligibleYears": [1],
  "participationType": "SOLO",
  "minTeamSize": 1,
  "maxTeamSize": 1,
  "capacity": 30,
  "capacityUnit": "PARTICIPANT",
  "seatsTaken": 0,
  "seatsRemaining": 30,
  "registrationStatus": "REGISTRATION_OPEN",
  "registrationOpen": true
}
```

`registrationStatus` is the **effective** status: an open event with no seats left reports
`SOLD_OUT`, derived from the live seat count, so it can never contradict `seatsRemaining`.
Events closed manually have `capacity: null` and `seatsRemaining: null`.

### Solo registration

```bash
curl -s -X POST http://localhost:8080/api/registrations \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "buildx",
    "participants": [
      {"rollNo":"1MS24CS001","fullName":"Asha Rao","email":"asha@mit.example.edu","yearLevel":1}
    ],
    "idempotencyKey": "b2f1c0de-0001"
  }'
```

`201 Created`, `Location: /api/registrations/1`

```json
{
  "eventId": "buildx",
  "eventName": "BuildX",
  "participationType": "SOLO",
  "team": null,
  "registrations": [
    {
      "registrationId": 1,
      "participant": {
        "participantId": 1,
        "rollNo": "1MS24CS001",
        "fullName": "Asha Rao",
        "email": "asha@mit.example.edu",
        "yearLevel": 1
      }
    }
  ],
  "seatsRemaining": 29,
  "registeredAt": "2026-09-24T15:21:04.882Z"
}
```

Replaying the same `idempotencyKey` returns `200 OK` with the original registration instead
of creating a second one — which matters on campus wifi, where a request can succeed and the
response never arrive.

### Team registration

```bash
curl -s -X POST http://localhost:8080/api/registrations \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "ideathon",
    "teamName": "Kernel Panic",
    "participants": [
      {"rollNo":"1MS24CS010","fullName":"Ravi Kumar","email":"ravi@mit.example.edu","yearLevel":1},
      {"rollNo":"1MS24CS011","fullName":"Neha Shah","email":"neha@mit.example.edu","yearLevel":1},
      {"rollNo":"1MS23IS004","fullName":"Imran Ali","email":"imran@mit.example.edu","yearLevel":2}
    ]
  }'
```

`201 Created` — Ideathon accepts years 1 and 2, so a mixed-year team is fine.

```json
{
  "eventId": "ideathon",
  "eventName": "Ideathon",
  "participationType": "TEAM",
  "team": { "teamId": 1, "name": "Kernel Panic", "captainRollNo": "1MS24CS010", "size": 3 },
  "registrations": [
    { "registrationId": 2, "participant": { "participantId": 2, "rollNo": "1MS24CS010", "...": "" } },
    { "registrationId": 3, "participant": { "participantId": 3, "rollNo": "1MS24CS011", "...": "" } },
    { "registrationId": 4, "participant": { "participantId": 4, "rollNo": "1MS23IS004", "...": "" } }
  ],
  "seatsRemaining": 39,
  "registeredAt": "2026-09-24T15:24:10.114Z"
}
```

Ideathon's capacity counts **teams**, not people, so a three-person team consumed one seat.
BuildX counts participants. This is a column (`capacity_unit`), not an assumption.

### My registrations

```bash
curl -s "http://localhost:8080/api/registrations/1"
curl -s "http://localhost:8080/api/registrations?rollNo=1MS24CS001"
```

```json
{
  "participant": {
    "participantId": 1, "rollNo": "1MS24CS001", "fullName": "Asha Rao",
    "email": "asha@mit.example.edu", "yearLevel": 1
  },
  "registrations": [
    {
      "registrationId": 1, "eventId": "buildx", "eventName": "BuildX",
      "participationType": "SOLO", "teamId": null, "teamName": null,
      "registeredAt": "2026-09-24T15:21:04.882Z"
    }
  ]
}
```

---

## 7. Errors

Every failure returns the same shape. Branch on `code`, never on the message text.

```json
{
  "timestamp": "2026-09-24T15:30:02.441Z",
  "status": 422,
  "code": "INVALID_TEAM_SIZE",
  "message": "Tech Debate requires teams of exactly 10 members. You submitted 9.",
  "path": "/api/registrations",
  "details": { "eventId": "tech-debate", "submittedTeamSize": 9, "minTeamSize": 10, "maxTeamSize": 10 }
}
```

| Code | Status | Raised when |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Payload failed Bean Validation. `details.fieldErrors` lists every field. |
| `MALFORMED_REQUEST` | 400 | Unparseable body, wrong parameter type, missing query parameter |
| `EVENT_NOT_FOUND` | 404 | Unknown event id |
| `PARTICIPANT_NOT_FOUND` | 404 | Unknown participant id or roll number |
| `REGISTRATION_CLOSED` | 409 | Event is not accepting registrations |
| `CAPACITY_FULL` | 409 | No seats left — BuildX at 30, Ideathon at its configured cap |
| `DUPLICATE_REGISTRATION` | 409 | Someone on the roster is already registered for this event |
| `TEAM_NAME_TAKEN` | 409 | Another team in this event uses that name |
| `PARTICIPANT_IDENTITY_CONFLICT` | 409 | Roll number exists with a different email or year |
| `INELIGIBLE_YEAR` | 422 | Participant's year is not eligible |
| `INVALID_TEAM_SIZE` | 422 | Roster outside the event's min/max |
| `SOLO_EVENT_REJECTS_TEAM` | 422 | Solo event sent multiple participants or a team name |
| `TEAM_NAME_REQUIRED` | 422 | Team event sent no team name |
| `DUPLICATE_PARTICIPANT_IN_ROSTER` | 422 | Same roll number twice on one roster |
| `INTERNAL_ERROR` | 500 | Unexpected failure |

**409 vs 422**: a 409 conflicts with current server state and might succeed if retried later;
a 422 breaks a rule of the event itself and will always fail if retried identically.

---

## 8. The rules, and where they live

Event rules are **data**, not code. `RegistrationRules` never mentions BuildX, never checks
for `tech-debate`, never hardcodes 10 or 30 — it reads the `event` row and applies it. Adding
an event or changing a team size is a migration.

| Event | Years | Participation | Team size | Capacity |
|---|---|---|---|---|
| Chess | 1, 2 | Solo | 1 | Manual closure |
| Tech Debate | 1, 2 | Team | **exactly 10** | Manual closure |
| FIX IT | 1, 2 | Team | 1–4 | Manual closure |
| Ideathon | 1, 2 | Team | 1–4 | **40 teams** (configurable) |
| BuildX | **1 only** | Solo | 1 | **30 participants** (hard) |
| Debugging | **1 only** | Solo | 1 | Manual closure |
| Rapid Research | **2 only** | Team | 1–2 | Manual closure |

A solo event is stored as a team of exactly one (`min = max = 1`), so the roster validator
has one code path instead of a special case. Tech Debate's "exactly 10" is `min = max = 10`.

### Capacity cannot be raced

Seats are claimed by one conditional statement:

```sql
UPDATE event
   SET seats_taken = seats_taken + :seats
 WHERE id = :eventId
   AND (capacity IS NULL OR seats_taken + :seats <= capacity);
```

Zero rows affected means sold out. PostgreSQL evaluates the predicate and applies the
increment under a single row lock, so the check cannot be stale by the time the increment
lands. The naive `SELECT count(*)` then `INSERT` oversells immediately under load, because
fifty concurrent requests all read 29 before any of them writes.

A `CHECK (seats_taken <= capacity)` constraint stands behind it, and
`UNIQUE (event_id, participant_id)` does the same job for duplicate registration. Those
constraints are the authoritative guards; the application-level checks upstream exist only
to produce a clearer message in the uncontended case.

---

## 9. Tests

```bash
./mvnw test
```

Tests run against a **real PostgreSQL** in Docker via Testcontainers, not H2. The capacity
guarantee depends on PostgreSQL's behaviour for a conditional UPDATE under concurrent
transactions, and the schema uses partial and expression indexes. An in-memory substitute
would pass these tests while the thing they protect stayed broken.

| Suite | Covers |
|---|---|
| `EventCatalogApiTest` | All seven events seeded, every rule matching CLAUDE.md, 404 shape |
| `RegistrationRulesApiTest` | Eligibility, roster shape, exactly-10, duplicates, closed events, idempotency, validation |
| `CapacityConcurrencyTest` | **50 simultaneous BuildX registrations → exactly 30 succeed**, derived `SOLD_OUT`, Ideathon's configurable team-counted capacity |

The concurrency test is the one that matters. It releases fifty requests together from a
latch and asserts exactly thirty win — not "at most thirty", because a correct
implementation must also not reject claims it should have accepted. A sequential test cannot
tell a correct implementation from a broken one here.

### Troubleshooting

**"Could not find a valid Docker environment"** — Docker Desktop is not running, or its
active context is on a pipe the Java client does not probe. Check with `docker context ls`.
If the active context is `desktop-linux`, set:

```bash
export DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'   # Windows + Docker Desktop
```

A related failure — the same message even though `docker info` works — comes from Docker
Engine 29 refusing API versions below 1.40. The POM pins `DOCKER_API_VERSION=1.43` for the
test JVM to avoid it.

---

## 10. Layout

```
backend/
├── docker-compose.yml              local PostgreSQL
├── .env.example                    every variable, documented
├── pom.xml
└── src/
    ├── main/
    │   ├── java/in/mittechkernel/registration/
    │   │   ├── RegistrationServiceApplication.java
    │   │   ├── config/             CORS
    │   │   ├── controller/         EventController, RegistrationController
    │   │   ├── dto/                request/response records — entities are never exposed
    │   │   ├── entity/             Event, Participant, Team, Registration + enums
    │   │   ├── exception/          ApiErrorCode, ApiException, GlobalExceptionHandler
    │   │   ├── repository/         Spring Data JPA + the conditional seat-claim UPDATE
    │   │   └── service/            EventService, ParticipantService,
    │   │                           RegistrationService, RegistrationRules
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/       V1 schema, V2 seed
    └── test/
        ├── java/in/mittechkernel/registration/
        │   ├── PostgresIntegrationTest.java   shared container + per-test reset
        │   ├── TestRequests.java
        │   ├── EventCatalogApiTest.java
        │   ├── RegistrationRulesApiTest.java
        │   └── CapacityConcurrencyTest.java
        └── resources/application-test.yml
```

`RegistrationRules` holds every rule and has no repository dependencies, so the ordering of
database work stays visible in `RegistrationService` rather than hidden behind a validator.

---

## 11. Not in this milestone

Authentication, JWT, Spring Security, admin endpoints, event execution states
(`LIVE`, `SUBMISSION_OPEN`, …), submissions, judging, results, WebSockets, the debugging
engine, Gemini, and the cinematic frontend.

Registration state (`REGISTRATION_OPEN` / `REGISTRATION_CLOSED` / `SOLD_OUT`) is modelled.
Event execution state deliberately is not — they are separate lifecycle concerns, and
execution is a later milestone.

There is no endpoint to open or close an event yet. Until the admin milestone, do it in SQL:

```sql
UPDATE event SET registration_status = 'REGISTRATION_CLOSED' WHERE id = 'buildx';
UPDATE event SET capacity = 60 WHERE id = 'ideathon';   -- configurable capacity
```
