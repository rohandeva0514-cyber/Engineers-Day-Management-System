# DEBUGGING ARENA — Architecture & UX Specification

Engineers' Day 2026 · MIT TECH KERNEL
Status: **design, not yet implemented.** No code is written from this document until it is approved.

This spec is written against the repository as it stands today:

- Backend `registration-service`, Spring Boot 3.5.6, Java 21, JPA + Flyway (V1–V11), PostgreSQL/Supabase,
  Spring Security with HTTP Basic guarding `/api/admin/**` and everything else public.
- Access codes already exist: `registration.access_code`, 8 chars from `[2-9A-HJ-NP-Z]`, unique partial
  index, issued only where `event.requires_access_code` is true (today: `debugging` only, set in V9).
- Frontend React 19 / Vite 8 / Tailwind v4 with `@theme` tokens in `styles/globals.css`, GSAP + Lenis,
  react-router 7, one `apiClient.ts` HTTP boundary, a separate in-memory-credential `admin/adminApi.ts`.

Everything below reuses those pieces rather than introducing parallel ones.

---

## Locked decisions

Settled by the project owner on 2026-09-29. These are no longer open, and where the first draft of this
document disagreed with them, the document has been corrected — the corrections are listed so the reasoning
is not lost.

| # | Decision |
|---|---|
| 1 | **Second device resumes.** A new code exchange mints a new session token and revokes the previous one. No strict refusal mode. |
| 2 | **Self-hosted Judge0** is the execution engine. |
| 3 | **Fixed 12 problems per language** — every participant in a language receives the same 12. No per-attempt selection. |
| 4 | **Scoring** EASY 100 / MEDIUM 200 / HARD 300. Tiebreak: **earlier final submission time**. |
| 5 | **The hidden-test count is never exposed** to a participant, in any form. |

Plus one input that arrived with them: the authoritative problem bank is **five JSON files** in
`backend/src/main/resources/debugging/`, holding 60 problems (12 per language, 4 per difficulty). They are
not to be regenerated or modified. Their `points` values are already 100/200/300, matching decision 4.

### Corrections applied to this document

1. **D3 rewritten.** The draft returned a hidden **aggregate** (`HIDDEN 6/8`) and made it a toggle
   (`arena_control.reveal_hidden_summary`). Decision 5 removes both. RUN still executes the full suite
   server-side — that is what keeps `✓ SOLVED` truthful — but returns visible detail plus a **binary**
   verdict only. The toggle column is deleted rather than defaulted off: a column that exists can be
   flipped by accident, and this one cannot be allowed to be.
2. **`strict_single_device` column deleted.** Decision 1 makes takeover the only behaviour, so the column
   is dead configuration. `session_takeovers` stays — it is the counter that makes code-sharing *visible*
   on the admin roster.
3. **`ProblemAssignmentService` → `ProblemBankService`.** With a fixed 12 there is no selection, no seed
   and no RNG; the service materialises the same twelve for everyone in a language.
4. **V13 (`debug_problem`, `debug_problem_test`) deleted.** The JSON files are the source of truth, loaded
   at startup into an immutable validated registry. This is strictly better for decision 5's guarantee:
   reference solutions and hidden tests then live in a server-side resource that the database never holds,
   so a database compromise cannot leak them. Migrations renumber to V12 / V13 / V14.
5. **Tiebreak corrected** from "earliest last-solve" to **earlier final submission time**.
6. **Execution engine narrowed** from "Judge0 (or Piston)" to self-hosted Judge0.
7. **Admin problem-bank browser is read-only.** The draft allowed disabling a problem mid-event; with a
   fixed 12 that would hand later starters an 11-problem mission and destroy comparability. A problem found
   broken during the event is handled by a scoring adjustment afterwards, never by mutating a live set.

---

## 0. The three decisions that shape everything else

Read these first; the rest of the document is consequence.

### D1 — The access code is not a session credential

The 8-character code is printed on a registration receipt, listed under My Registrations, and may be read
aloud at a desk. Sending it as a bearer on every run/submit request would make a long-lived, semi-public
string the key to a live attempt.

**Decision:** the code is exchanged **once** for an opaque 256-bit session token, stored server-side as a
SHA-256 hash on the attempt row, sent as `Authorization: Bearer <token>`. Revocable instantly (submit,
admin stop, session takeover). No JWT — that would add a dependency and a key to rotate in order to protect
one short-lived value that we are already storing a row for.

### D2 — The clock starts at START MISSION, not at code entry

**Amended 2026-09-29, at Phase B.** The original draft created no attempt row until START MISSION, and
issued a "pre-session" token bound only to the registration. That is replaced by an `INITIALIZED` attempt
row created at the access exchange. The reasoning is recorded below because the amendment reverses a
stated decision.

What the original decision was protecting: a student who types their code, reads the briefing, and then
has to move to a working machine must not have lost 45 minutes. **That protection is fully preserved** —
`INITIALIZED` does not start the clock, consume anything, or prevent a fresh start. `started_at` and
`expires_at` stay NULL until `POST /api/arena/attempt/start`.

What the original decision could not do: **session takeover needs somewhere to live.** Takeover requires a
persisted token hash and a persisted counter from the very first exchange. With no attempt row there is
nowhere to put them short of a second `arena_session` table — a second identity for the same participant,
with its own lifecycle to keep in step with the attempt's. One row, one token hash, one counter is simpler
and makes the session inseparable from the attempt it authorises.

It also moves the one-attempt guarantee earlier. `UNIQUE (registration_id)` now binds from the first
request rather than only after START, so two devices racing the access exchange collide in the database
instead of in application logic.

```
(no row) ──access exchange──▶ INITIALIZED ──start──▶ ACTIVE ──▶ SUBMITTED | EXPIRED | TERMINATED
                              clock NOT running      clock running
```

`INITIALIZED` is therefore not the stored `NOT_STARTED` the original draft argued against: that one was
rejected because it would have been created at a moment where an interruption cost the student their
attempt, and this one is not.

### D3 — RUN executes the full suite; only the visible half is reported

The spec asks for a `✓ SOLVED` marker per problem, which has to be derivable *during* the mission, and
also forbids the frontend ever seeing hidden tests. Those are only compatible if the server runs
everything and reports visible detail plus a **binary** verdict:

```
COMPILE        OK           12 ms
VISIBLE TESTS  3 / 3        ✓ ✓ ✓
STATUS         FAULT REMAINS
```

Test names, inputs, expected outputs, counts, the reference solution and the bug explanation never cross
the wire. Per decision 5 there is no hidden-test count in any payload and no configuration that could
produce one.

What the verdict does disclose is one bit — *all hidden tests passed*, or *not*. That bit is unavoidable:
it **is** the `✓ SOLVED` indicator the spec requires, and a marker that could not distinguish solved from
unsolved would be a lie. One bit per run is not a count and cannot be accumulated into one, because a
failing run says nothing about how many tests failed or which.

**Consequence:** `SOLVED` is a *server-recorded* fact on `attempt_problem`, set when a run passes every
test. At final submission the backend re-runs the stored draft for every problem and scores from that run —
the in-mission status is a display cache, never the scoring input.

---

## 1. Screen-by-screen UX flow

Nine screens, one route (`/arena`), driven by a phase machine. There is no back button between phases —
the phase is derived from server state on every entry, so refresh is always safe and never grants anything.

### S0 · GATE — arena offline

Reached whenever `GET /api/arena/status` reports `OFFLINE`. Polled every 10 s, so it flips to S1 on its own
when the admin opens the arena; nobody has to tell 30 students to refresh.

```
        ● OFFLINE                      (dot: --color-closed, slow 2s pulse)

        DEBUGGING ARENA
        ENGINEERS DAY 2026

        The arena has not started yet.
        Mission control will open it when the event begins.

        ── system standby ──────────────────────────────
        WAITING FOR MISSION CONTROL▮
```

No input is rendered at all. A disabled field invites people to try it and then complain it is broken.

### S1 · ACCESS CODE

```
        ● ACTIVE                       (dot: --color-signal)

        DEBUGGING ARENA
        ENGINEERS DAY 2026

        ENTER ACCESS CODE

        ┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐┌──┐
        │ K││ 4││ M││ 2││ X││▮ ││  ││  │
        └──┘└──┘└──┘└──┘└──┘└──┘└──┘└──┘

        [ VERIFY ACCESS ]

        Eight characters. Case does not matter.
```

Eight discrete cells, one `<input>` per cell in a labelled group. Paste distributes across cells; Backspace
on an empty cell steps back; every character is upper-cased on input. The button enables at 8 characters.

**Verifying** — the three log lines are chained to the real request, never faked:

```
        ▸ VERIFYING ACCESS…
        ▸ AUTHENTICATING PARTICIPANT…
        ▸ ACCESS GRANTED
```

A horizontal scan line (`anim-scan-sweep`, already in `globals.css`) crosses the panel while the request is
in flight. If the response arrives in 90 ms the lines still print over ~700 ms — the sequence reads as the
system working, not as a stall. If the response takes 4 s, the third line simply waits.

**Refused** — one message for every cause. Invalid code, code for another event, and code-not-for-debugging
are indistinguishable, exactly as `AccessCodeService` already does it:

```
        ▸ ACCESS DENIED
        That code was not recognised. Check it and try again.
```

The panel border flips to `--color-danger` and jitters ±3 px three times over 120 ms (suppressed under
reduced motion — the colour and the text carry the meaning on their own).

Two refusals are *not* generic, because they are legitimate states the student must understand:

| Server code | Screen |
|---|---|
| `ARENA_OFFLINE` | falls back to S0 |
| `ATTEMPT_ALREADY_SUBMITTED` | S8 · MISSION COMPLETE (read-only) |
| `ATTEMPT_EXPIRED` | S8 variant · MISSION TIME EXPIRED |
| `ATTEMPT_ACTIVE` (resume) | jumps straight to S5 with the running clock |

### S2 · PARTICIPANT IDENTIFICATION

Exactly the six fields `AccessCodeVerificationResponse` already returns — no email, no phone, no roll
number, no participant id. That DTO's field list is the specification and it does not change.

```
        ┌─ ACCESS GRANTED ──────────────────────────── ●VERIFIED ─┐
        │                                                         │
        │  PARTICIPANT                                            │
        │  Rohan Devadiga                                         │
        │                                                         │
        │  BRANCH                 YEAR            DIVISION        │
        │  Computer Engineering   First Year      A               │
        │                                                         │
        │  EVENT                  STATUS                          │
        │  Debugging              VERIFIED ✓                      │
        │                                                         │
        │                                     [ CONTINUE ]        │
        └─────────────────────────────────────────────────────────┘
```

Fields resolve on a 60 ms stagger using the existing `animations/scramble.ts`. Auto-advances after 6 s if
untouched, so a student who is reading rather than clicking is not stranded.

### S3 · LANGUAGE SELECTION

```
        SELECT YOUR DEBUGGING LANGUAGE
        Your problems come from this language's bank. It cannot be
        changed once the mission starts.

        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │    C     │ │   C++    │ │   JAVA   │
        │  gcc 13  │ │ g++ 13   │ │ JDK 21   │
        └──────────┘ └──────────┘ └──────────┘
        ┌──────────┐ ┌──────────┐
        │  PYTHON  │ │JAVASCRIPT│
        │  3.12    │ │ node 22  │
        └──────────┘ └──────────┘
```

Languages come from `GET /api/arena/status`, not a frontend constant — an unavailable runtime is removed
server-side and the card disappears rather than failing at the first run.

On select: the other four dim to 30 % and drift 8 px outward, the chosen card centres, and a
`LANGUAGE LOCKED` stamp settles in. Selection is still reversible here (a `CHANGE` link) — nothing has been
sent to the server yet. The lock becomes real at `attempt/start`.

### S4 · MISSION BRIEFING

Static content, typed in over ~1.2 s, skippable with a click or any key.

```
        MISSION: DEBUGGING ARENA
        SYSTEM STATUS: ONLINE            LANGUAGE: C++

        You have been assigned a debugging mission.

        ZONES        EASY · MEDIUM · HARD        4 problems each
        TIME         45 MINUTES
        OBJECTIVE    Identify the faults. Repair the code.
                     Pass the hidden tests.

        · Move between zones and problems in any order, at any time.
        · Your code is saved automatically as you type.
        · Submitting ends the mission for you. It cannot be undone.

        [ START MISSION ]   ← this starts your 45-minute clock
```

The clock warning sits on the button, not in a paragraph above it. That is the one sentence that must not
be missed.

### S5 · MISSION CONSOLE — the arena

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ ▣ DEBUGGING ARENA            MISSION TIME  38:42        ● ACTIVE   [ SUBMIT ] │
│   ENGINEERS DAY 2026         C++                        3 / 12 SOLVED         │
├───────────────────────────────────────────────────────────────────────────────┤
│  ZONE   [ EASY ]   [ MEDIUM ]   [ HARD ]        ✓✓◐○ │ ◐○○○ │ ○○○○            │
├──────────────────────┬────────────────────────────────────────────────────────┤
│ DEBUGGING TARGET     │ ⟨ E-02 ⟩ off_by_one.cpp              ○ NOT ATTEMPTED   │
│                      │ ──────────────────────────────────────────────────────  │
│ E-02 · Array bounds  │  1 │ #include <vector>                                  │
│                      │  2 │                                                    │
│ The function should  │  3 │ int sum_prefix(const std::vector<int>& v, int k) { │
│ return the sum of    │  4 │     int total = 0;                                 │
│ the first k values.  │  5 │     for (int i = 0; i <= k; ++i)                   │
│ It does not.         │  6 │         total += v[i];                             │
│                      │  7 │     return total;                                  │
│ INPUT                │  8 │ }                                                  │
│ v — up to 1000 ints  │                                                         │
│ k — 0 ≤ k ≤ v.size() │                                                         │
│                      │                                                         │
│ OUTPUT               │                                                         │
│ The prefix sum.      │                                                         │
│                      │                                                         │
│ SAMPLE               │                                                         │
│ in  [1,2,3,4] 2      │                                                         │
│ out 3                │                                                         │
├──────────────────────┴────────────────────────────────────────────────────────┤
│ TEST RESULTS                                          saved 2s ago            │
│ COMPILE  OK  12ms   VISIBLE 3/3 ✓✓✓                ▸ FAULT REMAINS            │
│                                                                               │
│ [ RUN CODE  ⌘↵ ]                                         [ SUBMIT MISSION ]   │
└───────────────────────────────────────────────────────────────────────────────┘
```

Three zones, twelve problems, all reachable from the moment the mission starts. Zone tabs carry a compact
status strip (`✓✓◐○`) so the whole board is legible without opening anything. There is no sequential
unlock anywhere in the design — the problem list is materialised in full at `attempt/start`.

Problem status: `○ NOT ATTEMPTED` · `◐ ATTEMPTED` (a draft was saved or a run executed) · `✓ SOLVED`
(a run passed every test, server-recorded).

`SUBMIT MISSION` lives in the header *and* the dock. Both open the same dialog; there is no other way to
finalize, and it is never one stray click away from `RUN CODE`.

### S6 · FINALIZE DIALOG

```
        ┌─ FINALIZE MISSION? ───────────────────────────────┐
        │                                                   │
        │  You have 3 of 12 problems solved and 24:18 left. │
        │                                                   │
        │  After submission you cannot:                     │
        │    · edit or run code                             │
        │    · open more problems                           │
        │    · submit again                                 │
        │                                                   │
        │  This cannot be undone.                           │
        │                                                   │
        │              [ CANCEL ]   [ CONFIRM SUBMISSION ]  │
        └───────────────────────────────────────────────────┘
```

Focus lands on `CANCEL`. `Escape` cancels. `CONFIRM` requires a real click — no Enter-key path, because
Enter is muscle memory from the editor. Solved count and remaining time are stated so nobody submits at
`41:00` by accident.

### S7 · SUBMITTING

Blocking, non-cancellable, ~2–6 s while the backend re-runs every stored draft:

```
        ▸ LOCKING SUBMISSION…
        ▸ VERIFYING 12 TARGETS…
        ▸ MISSION RECORDED
```

### S8 · MISSION COMPLETE

```
        ● MISSION COMPLETE

        SUBMISSION RECORDED
        RESULT PENDING

        PARTICIPANT   Rohan Devadiga
        LANGUAGE      C++
        SUBMITTED     14:32:07
        DURATION      21 min 42 s
        REFERENCE     ATT-00042

        Your submission has been recorded. Results are published by
        mission control after evaluation.

        You may close this window.
```

No score, no rank, no per-problem breakdown. `RESULT PENDING` is the terminal state for the participant.
Re-entering the access code returns to exactly this screen.

**Variants, same frame:**
- Timer hit zero: `MISSION TIME EXPIRED` — "Your work up to 00:00 has been recorded." Identical otherwise.
- Admin stopped the arena: `MISSION CONTROL HAS ENDED THE ARENA` — "Your work has been recorded."

The student is never told their attempt was lost, because it is not: every terminal path finalizes from the
last saved draft.

---

## 2. Information architecture

```
/arena                        the whole experience, one route, phase-driven
  ?                           no sub-paths — a URL must never address a phase,
                              or a bookmarked /arena/problems/E-02 would be a
                              way to appear to be somewhere the server says
                              you are not

/admin  →  ARENA tab          arena control, live roster, results
/verify                       UNCHANGED — marshal check-in, different job
```

**Why one route with no sub-paths.** Every other decision here says the server owns the phase. A URL is a
client-supplied claim about phase. Keeping the arena at a single address means the phase can only ever come
from `GET /api/arena/attempt`, and browser Back cannot produce a screen the server disagrees with.

**Content ownership**

| Data | Lives | Reaches the browser |
|---|---|---|
| Arena status, duration, languages | `arena_control` | yes, public |
| Participant identity (6 fields) | `participant` + `registration` | yes, after code exchange |
| Problem statement, I/O spec, visible tests, buggy source | JSON bank resource | yes, for assigned problems only |
| Hidden tests, expected outputs, corrected code, bug list | JSON bank resource (`hidden_tests`, `corrected_code`, `bugs`) | **never** |
| Hidden-test pass counts | `arena_run` | **never** to a participant; admin only |
| Draft code | `attempt_problem.draft_code` | yes, own attempt only |
| Run results | `arena_run` | visible detail + hidden aggregate |
| Score, rank, test-level results | `arena_attempt.score`, `attempt_problem.*` | **never** to a participant; admin only |

---

## 3. Component hierarchy

### Frontend

```
app/router.tsx
└── /arena → <ArenaRoute/>                  own shell: no RootLayout, no SiteHeader,
                                            no Lenis (smooth scroll fights a code editor)
    └── <ArenaProvider>                     reducer + polling + clock sync + token
        └── <ArenaFrame>                    viewport, surface-grid, scanlines, vignette
            │
            ├── OFFLINE     <ArenaOfflineScreen/>
            ├── ACCESS      <AccessCodeScreen/>
            │                 └── <CodeCellInput/> <BootLog/> <ScanOverlay/>
            ├── VERIFIED    <IdentityScreen/>
            │                 └── <IdentityCard/>
            ├── LANGUAGE    <LanguageSelectScreen/>
            │                 └── <LanguageCard/>×n
            ├── BRIEFING    <MissionBriefingScreen/>
            │                 └── <TypedBlock/>
            ├── ACTIVE      <MissionConsole/>
            ├── SUBMITTING  <FinalizeSequence/>
            └── CLOSED      <MissionCompleteScreen/>   3 variants: submitted | expired | ended

<MissionConsole>
├── <ConsoleHeader>
│     ├── <MissionClock/>           server-anchored countdown, tabular-nums
│     ├── <MissionStatusPill/>      ● ACTIVE / ● SAVING / ● OFFLINE
│     ├── <SolvedCounter/>
│     └── <SubmitMissionButton/>
├── <ZoneRail>                      EASY | MEDIUM | HARD + per-zone status strip
├── <ProblemRail>                   chips: ○ ◐ ✓, keyboard 1–4
├── <ConsoleBody>
│     ├── <TargetBrief>             title, brief, constraints, I/O, <SampleCase/>×n
│     └── <CodeTerminal>
│           ├── <TerminalBar/>      filename, status, reset-to-original
│           ├── <CodeEditor/>       CodeMirror 6, lazy-loaded per language
│           └── <DraftIndicator/>   "saved 2s ago" / "saving…" / "offline — retrying"
├── <OutputDock>
│     ├── <RunSummary/>             compile · visible · hidden · verdict
│     ├── <TestResultList/>         visible tests only, 40 ms stagger
│     ├── <RunButton/>              ⌘↵ / Ctrl+↵, rate-limit aware
│     └── <SubmitMissionButton/>
└── <FinalizeDialog/>               focus trap, Escape cancels

components/arena/   HudPanel · StatusDot · TechLabel(existing) · ScanOverlay · BootLog · ArenaButton
hooks/arena/        useArena · useMissionClock · useArenaHeartbeat · useDraftAutosave · useCodeRunner
services/arena/     arenaApi.ts · arenaSession.ts · arenaTypes.ts
styles/             arena.css   (tokens reused from globals.css; no new palette)
```

`ArenaProvider` is the only component that knows the session token exists. Nothing below it can send a
request; they call hooks, the hooks call `arenaApi`, and `arenaApi` is the only module that attaches the
bearer — mirroring how `services/apiClient.ts` is already the single `fetch` boundary.

### Backend

```
in.mittechkernel.registration.arena
├── controller/   ArenaPublicController      /api/arena/status, /api/arena/access
│                 ArenaSessionController     /api/arena/**          (bearer)
│                 ArenaAdminController       /api/admin/arena/**    (existing Basic auth)
├── service/      ArenaControlService        open/stop/duration, the OFFLINE|ACTIVE|ENDED machine
│                 ArenaAccessService         code → pre-session, resume, refusal shaping
│                 ArenaSessionService        token mint / hash / resolve / revoke
│                 ArenaAttemptService        start · resume · expire · submit · terminate
│                 ProblemBankService         loads + validates the 5 JSON banks at startup;
│                                            materialises the same fixed 12 per language
│                 DraftService               autosave with revision guard
│                 RunService                 rate limit → execute → record → status
│                 ScoringService             deterministic final evaluation
│                 ArenaExpirySweeper         @Scheduled 60s, marks overdue attempts EXPIRED
├── execution/    CodeExecutionEngine        interface
│                 Judge0ExecutionEngine      primary
│                 DisabledExecutionEngine    dev/fallback, returns EXECUTION_UNAVAILABLE
│                 ExecutionRequest/Result
├── security/     ArenaTokenFilter           Bearer → ArenaPrincipal, before the Basic filter
│                 ArenaPrincipal             (attemptId | registrationId, phase)
├── entity/       ArenaControl · ArenaAttempt · AttemptProblem · DebugProblem
│                 DebugProblemTest · ArenaRun · ArenaAuditEntry
├── repository/   …
└── dto/          ArenaStatusResponse · ArenaAccessResponse · AttemptStateResponse
                  ProblemView · RunResultView · SubmitReceipt · admin/ArenaAdminDtos
```

`ProblemView` is built from a **constructor-expression JPQL projection** that never selects
`reference_solution`, `bug_category`, `fix_explanation`, or non-visible test rows. Leaking a hidden test
would require adding a field to that projection — not forgetting to strip one. That is the same discipline
`AccessCodeVerificationResponse` already uses for contact details.

---

## 4. State machines

### 4.1 Arena (one row, admin-controlled)

```
        ┌──────────────── admin: STOP ────────────────┐
        ▼                                             │
    OFFLINE ───── admin: START ─────▶ ACTIVE ─────────┘
        ▲                                │
        │                                │ admin: END
        └──── admin: REOPEN ───────  ENDED
```

- `OFFLINE` — no access exchange, no start. Existing attempts are frozen, not destroyed.
- `ACTIVE` — the only state in which attempts start, run, or submit.
- `ENDED` — running attempts are finalized in place from their last saved draft; participants see
  `MISSION CONTROL HAS ENDED THE ARENA`. Reopening does **not** resurrect them.

`STOP` (→ OFFLINE) and `END` are deliberately different: STOP is "pause, something is wrong, I will
resume"; END is "the event is over, finalize everyone". Conflating them turns a projector failure into a
mass submission.

### 4.2 Attempt (one row per registration, `UNIQUE(registration_id)`)

```
   (no row)
      │  POST /arena/access  { code }           guards: arena ACTIVE, code valid
      ▼                                         clock NOT started
   INITIALIZED
      │  POST /attempt/start  { language }      guards: arena ACTIVE, still INITIALIZED,
      ▼                                                 supported language
   ACTIVE ──────── POST /attempt/submit ──────▶ SUBMITTED   ← scored, terminal
      │
      ├─────────── now() ≥ expires_at ────────▶ EXPIRED     ← scored, terminal
      │            (lazy on any request, plus a 60 s sweep)
      │
      ├─────────── arena → ENDED ─────────────▶ TERMINATED  ← scored, terminal
      │
      └─────────── admin terminate ───────────▶ TERMINATED  ← scored, terminal, audited
```

Every terminal transition finalizes from the last saved draft. There is no path that discards work, and
there is no path out of a terminal state except an audited `admin reset`, which creates a *new* attempt row
and marks the old one superseded — the history is never edited.

`INITIALIZED` is the checked-in-but-not-started state (see D2). A student may sit in it indefinitely,
change language freely, close the browser, move machines, and re-enter their code — none of it starts the
clock or costs them anything. Only `POST /attempt/start` does, and it is the one button that says so.

### 4.3 Attempt-problem

```
   ASSIGNED ──── draft saved ────▶ ATTEMPTED ──── run passes all tests ────▶ SOLVED
      ○                              ◐                                        ✓
                                     ▲                                         │
                                     └────── later run regresses ──────────────┘
```

`SOLVED` is not sticky. If a later run breaks the fix, the marker returns to `ATTEMPTED` — because the
stored draft is what gets scored, and the badge must describe the code that will actually be submitted.

### 4.4 Frontend phase (derived, never stored as truth)

```
BOOT → OFFLINE ⇄ ACCESS → VERIFIED → LANGUAGE → BRIEFING → ACTIVE → SUBMITTING → CLOSED
         ▲                                                    │
         └──────── any 401/ARENA_SESSION_INVALID ─────────────┘
```

Recomputed from `GET /api/arena/attempt` on mount, on focus, and on every heartbeat. `sessionStorage` holds
the token only so a refresh does not require retyping the code; it is a convenience with zero authority,
and a rejected token silently returns the student to `ACCESS`.

---

## 5. API

### Public — no credentials

```
GET  /api/arena/status
  → 200 { eventId, status: OFFLINE|ACTIVE|ENDED, serverTime, durationSeconds,
          languages: [{ id:"cpp", label:"C++", runtime:"g++ 13" }, …] }
```
Polled every 10 s by S0/S1. Cheap, cacheable for 2 s, reveals nothing.

```
POST /api/arena/access            { code }
  → 200 { sessionToken, sessionExpiresAt, serverTime,
          participant { fullName, branch, division, yearLevel, eventName },
          attempt: null                                   ← go to LANGUAGE
                 | { state:"ACTIVE", expiresAt, language, solvedCount }   ← resume ACTIVE
                 | { state:"SUBMITTED"|"EXPIRED"|"TERMINATED", submittedAt } ← go to CLOSED
          languages: [...] }
  → 404 ACCESS_CODE_INVALID     one refusal for every cause (existing discipline)
  → 409 ARENA_OFFLINE
  → 429 RATE_LIMITED
```

### Session — `Authorization: Bearer <sessionToken>`

```
POST /api/arena/attempt/start     { language }
  → 201 { attemptId, state:"ACTIVE", startedAt, expiresAt, serverTime, language,
          zones: [{ difficulty:"EASY", problems:[{ ref:"E-01", title, status:"ASSIGNED" }, …] }, …] }
  → 409 ATTEMPT_ALREADY_EXISTS · ARENA_OFFLINE
  → 422 LANGUAGE_NOT_SUPPORTED

GET  /api/arena/attempt
  → 200 { state, expiresAt, remainingSeconds, serverTime, language, solvedCount, zones[…] }
        the single resume/rehydrate call

GET  /api/arena/problems/{ref}
  → 200 ProblemView { ref, difficulty, title, brief, constraints, inputSpec, outputSpec,
                      sampleTests:[{ input, expectedOutput }],
                      starterCode, draftCode, draftRevision, status, filename }
  → 403 PROBLEM_NOT_ASSIGNED        ref not in this attempt

PUT  /api/arena/problems/{ref}/draft   { code, revision }
  → 200 { revision, savedAt }
  → 409 DRAFT_STALE { currentRevision }   another device saved first
        debounced 800 ms, flushed on blur / problem switch / tab hide

POST /api/arena/problems/{ref}/run     { code }
  → 200 RunResultView {
          compile: { ok, durationMs, diagnostics },        truncated, 4 KB cap
          visible: [{ name, passed, input, expected, actual, durationMs }],
          verdict: "SOLVED" | "FAULT REMAINS" | "COMPILE ERROR" | "TIMEOUT" | "RUNTIME ERROR",
          problemStatus, runsRemaining }
        NO hidden-test count, ratio, or per-test detail — decision 5
  → 429 EXECUTION_RATE_LIMITED { retryAfterSeconds }
  → 503 EXECUTION_UNAVAILABLE
  → 409 ATTEMPT_EXPIRED · ATTEMPT_ALREADY_SUBMITTED · ARENA_OFFLINE

POST /api/arena/attempt/submit    { confirm: true }
  → 200 SubmitReceipt { state:"SUBMITTED", submittedAt, reference:"ATT-00042",
                        durationSeconds, language }
        NO score, NO per-problem breakdown
  → 409 ATTEMPT_ALREADY_SUBMITTED · ATTEMPT_EXPIRED

GET  /api/arena/session
  → 200 { arenaStatus, attemptState, serverTime, expiresAt, remainingSeconds }
        heartbeat, every 10 s; the wire that makes admin STOP reach a running console
```

### Admin — existing HTTP Basic on `/api/admin/**`

```
GET   /api/admin/arena
  → { control: { status, durationSeconds, openedAt, endedAt, updatedAt, updatedBy },
      counters: { registered, notStarted, active, submitted, expired, terminated },
      languageSpread: { cpp: 11, python: 7, … },
      executionEngine: { name, healthy, queueDepth } }

PATCH /api/admin/arena/status      { status: ACTIVE | OFFLINE | ENDED }
PATCH /api/admin/arena/settings    { durationMinutes }
      duration applies to attempts started AFTER the change; running attempts keep
      the expires_at stamped at their own start

GET   /api/admin/arena/attempts    ?state=&language=&search=
  → [{ attemptId, fullName, rollNo, branch, division, year, language, state,
       startedAt, expiresAt, remainingSeconds, solved: { easy, medium, hard },
       runCount, sessionTakeovers, lastSeenAt }]

GET   /api/admin/arena/attempts/{id}       full detail incl. per-problem drafts and runs
POST  /api/admin/arena/attempts/{id}/extend      { minutes, reason }   audited
POST  /api/admin/arena/attempts/{id}/terminate   { reason }            audited
POST  /api/admin/arena/attempts/{id}/reset       { reason }            audited, break-glass
GET   /api/admin/arena/results                   scores, rank, tiebreak trace
GET   /api/admin/arena/results/export.csv
```

### New `ApiErrorCode` values

Added to the existing enum, and — this is easy to forget — to `KNOWN_CODES` in
`frontend/src/services/apiClient.ts`, or the UI will collapse every one of them into a generic `UNKNOWN`:

```java
ARENA_OFFLINE(CONFLICT)              ARENA_ENDED(CONFLICT)
ARENA_SESSION_INVALID(UNAUTHORIZED)  ATTEMPT_ALREADY_EXISTS(CONFLICT)
ATTEMPT_ALREADY_SUBMITTED(CONFLICT)  ATTEMPT_EXPIRED(CONFLICT)
ATTEMPT_NOT_FOUND(NOT_FOUND)         PROBLEM_NOT_ASSIGNED(FORBIDDEN)
LANGUAGE_NOT_SUPPORTED(UNPROCESSABLE_ENTITY)
DRAFT_STALE(CONFLICT)                EXECUTION_RATE_LIMITED(TOO_MANY_REQUESTS)
EXECUTION_UNAVAILABLE(SERVICE_UNAVAILABLE)
RATE_LIMITED(TOO_MANY_REQUESTS)
```

---

## 6. Responsibility split

| Concern | Backend | Frontend |
|---|---|---|
| Arena open/closed | **authority** — every mutating call re-checks | renders status, polls |
| Access code valid | **authority** | collects 8 chars, shows one refusal |
| Participant identity | **authority** — releases 6 fields | displays |
| One attempt per participant | **`UNIQUE(registration_id)`** | never asserts it |
| Language lock | **stored on attempt at start** | picker only |
| 45-minute deadline | **`expires_at`, checked per request + swept** | *displays* a countdown |
| Problem assignment | **authority**, deterministic, persisted | renders the assigned list |
| Hidden tests / solution | **never leaves the server** | never receives them |
| Code execution | **sandboxed, rate-limited, off-host** | shows the result |
| `SOLVED` | **recorded from a server-side run** | renders the badge |
| Final submission | **once, irreversible, re-runs stored drafts** | confirmation dialog |
| Score & rank | **authority, never sent to participants** | shows `RESULT PENDING` |
| Admin control | **authenticated, audited** | buttons |
| Cinematics, layout, motion | — | **authority** |

The frontend's timer is decoration. If it drifts, reads 03:12 at true 00:00, or is tampered with in
devtools, the next request is refused with `ATTEMPT_EXPIRED` and the console switches to the expired screen.

---

## 7. Admin panel

A fourth tab in the existing `/admin` console, in the panel's established density-over-spectacle style —
no cinematics, no animation beyond a status dot.

```
┌─ DEBUGGING ARENA CONTROL ────────────────────────────────────────────────┐
│                                                                          │
│  STATUS   ● ACTIVE        opened 13:47   duration 45 min                 │
│                                                                          │
│  [ STOP ARENA ]   [ END & FINALIZE ALL ]   [ duration: 45 ▾ ]            │
│                                                                          │
│  REGISTERED 28    NOT STARTED 3    ACTIVE 19    SUBMITTED 6    EXPIRED 0 │
│  LANGUAGES  C++ 11 · PYTHON 7 · JAVA 6 · C 3 · JS 1                      │
│  JUDGE      ● healthy   queue 0   median 340 ms                          │
├──────────────────────────────────────────────────────────────────────────┤
│  NAME              ROLL      LANG    STATE      LEFT    SOLVED   RUNS    │
│  Rohan Devadiga    24CE041   C++     ACTIVE     24:18   3/12     17   ⋯  │
│  Aisha Khan        24IT009   PYTHON  SUBMITTED  —       7/12     41   ⋯  │
│  …                                                                       │
└──────────────────────────────────────────────────────────────────────────┘
```

Requirements:

1. `START` / `STOP` / `END & FINALIZE ALL`. `END` is behind a typed confirmation — it is the only control
   that terminates other people's work.
2. Live roster, auto-refreshing every 10 s, filterable by state / language / name.
3. Per-attempt `EXTEND` (+N minutes, reason required), `TERMINATE`, and break-glass `RESET`. A machine will
   fail on event day; an organiser needs a legitimate answer that is not "sorry".
4. Every one of those writes an `arena_audit` row: actor, action, target, reason, timestamp. Non-negotiable
   — these controls change competition outcomes and must be explainable afterwards.
5. Judge health, so "everyone's runs are failing" is visible on the dashboard rather than discovered via a
   queue of students.
6. Results view + CSV, opened only after evaluation. Publication to participants is a **separate**,
   deliberate action; reaching `SUBMITTED` never reveals a score.
7. Problem bank browser, **read-only**: list and per-problem solve rate. There is deliberately no
   disable control — every participant in a language gets the same fixed 12 (decision 3), so disabling one
   mid-event would hand later starters an 11-problem mission and destroy comparability. A problem found
   broken during the event is corrected by a scoring adjustment afterwards, which is auditable, rather
   than by mutating a live set, which is not.

---

## 8. Animation & interaction specification

Budget: **one cinematic moment (briefing → console), everything else under 200 ms.** The console is a
working environment for 45 minutes; anything that animates repeatedly there becomes an irritant by minute
five. All of this reuses existing tokens (`--ease-out-expo`, `--ease-in-out-quart`, `.anim-scan-sweep`,
`.surface-scanlines`, `.caret`, `.clip-notch`) — no new palette, no new easing vocabulary.

| Moment | Spec |
|---|---|
| Arena boot (S0/S1 entry) | HUD rails draw in from four edges, 420 ms, `--ease-out-expo`; status dot fades up last |
| Code-cell focus | border `--color-line-bright` → `--color-tech`, 120 ms; caret uses the shared `.caret` blink |
| Verifying | `.anim-scan-sweep` over the panel + 3 log lines at 220 ms intervals, chained to the real request |
| Access granted | panel border → `--color-signal`, single 300 ms glow ramp using `.glow-signal`, then settle |
| Access denied | border → `--color-danger`; translateX ±3 px × 3 over 120 ms; **no shake under reduced motion** |
| Identity reveal | fields stagger 60 ms; name resolves via existing `animations/scramble.ts`, 400 ms |
| Language select | unchosen cards → opacity .3 + 8 px outward drift, 240 ms; `LANGUAGE LOCKED` stamp scales 1.04 → 1, 180 ms |
| Briefing type-on | 18 ms/char, skippable on any key or click; total ≤ 1.2 s |
| **Briefing → console** | the one cinematic beat: briefing panel border expands to the viewport edges while header, zone rail, brief, editor and dock slide in from their own edges. GSAP timeline, 900 ms, `--ease-out-expo`, overlapping by 60 % so it reads as one motion, not five |
| Problem switch | brief panel crossfades + 6 px rise, 160 ms. **Editor content swaps instantly** — animating an editor reads as lag, not polish |
| Draft saving | `saving…` → `saved 2s ago`, opacity only. Never a spinner: it fires every few seconds |
| Run — pending | 2 px indeterminate scan bar across the dock; `RUN` becomes `RUNNING…` and disables |
| Run — results | visible tests stream in at 40 ms stagger; pass = `--color-signal` dot, fail = `--color-danger` dot; verdict line last. Nothing in this sequence is derived from hidden-test counts — the dock has none to render |
| First solve of a problem | problem chip `○/◐` → `✓`, 260 ms; one restrained `.glow-signal` pulse. Once, never repeated |
| Clock thresholds | `>10:00` `--color-ink` · `≤10:00` `--color-tech` · `≤5:00` `--color-orange` + 1 px border pulse **on the clock cell only** · `≤1:00` `--color-danger`, pulse at 2×. Never full-screen, never a sound, never a modal |
| Finalize dialog | backdrop blur 8 px + 200 ms fade; panel rises 12 px. Focus traps on `CANCEL` |
| Submitting | 3 log lines, 320 ms apart, chained to the request |
| Mission complete | rails retract to a single centred panel, 600 ms |

**Reduced motion** (`prefers-reduced-motion: reduce`, already handled globally in `globals.css`): every
transform and every sweep is replaced by an instant state change. The boot and verification log lines still
print — they carry information — but without per-character delay. No information is animation-only anywhere
in this design; every animated reveal has a settled end state that *is* the reduced-motion state.

**Keyboard** — this is a competition; hands stay on the keys:

```
⌘↵ / Ctrl+↵     run code                 ⌘S / Ctrl+S    force draft save
1 2 3 4         switch problem            [ ]            previous / next problem
⌘1 ⌘2 ⌘3        switch zone               Esc            close dialog / leave editor focus
⌘⇧↵             open finalize dialog      Tab            indent inside the editor,
                                                         focus-move outside it
```

A persistent `?` opens a shortcut sheet. `Tab` inside the editor must indent, not move focus — but `Esc`
then `Tab` must escape, or keyboard users are trapped in the editor.

---

## 9. Responsive behaviour

The editor is the product. Everything else yields to it.

| Width | Layout |
|---|---|
| **≥1440** | Full console. Brief 400 px · editor fluid · dock 240 px tall. The target experience. |
| **1280–1439** | Same, brief 340 px, dock 200 px. |
| **1024–1279** | Brief collapses to an overlay drawer behind a `BRIEF` button (auto-opened on problem switch, dismissed on first keystroke). Editor takes the full width. Dock 180 px. |
| **768–1023** (tablet) | Single column: header · zone segmented control · problem chips · collapsible brief · editor `55vh` · dock as a bottom sheet with a drag handle. |
| **<768** (phone) | Not blocked, but honest: a dismissible banner — *"This mission is built for a laptop. You can work here, but the editor is cramped."* Brief and dock become sheets; editor gets `48vh`; the clock and `SUBMIT` stay pinned. A student whose laptop dies mid-mission must still be able to finish. |
| Landscape phone | Treated as tablet. |

Non-negotiables at every size: the mission clock is always visible; `SUBMIT MISSION` is always reachable;
the editor is never smaller than 12 visible lines; code never wraps silently — horizontal scroll with a
visible affordance, because wrapped C++ misleads about line numbers, and line numbers are how people talk
about bugs.

---

## 10. Security

**Authentication & session**
- Access code exchanged once; never sent again. Session token is 32 bytes from `SecureRandom` (the same
  reasoning `AccessCodeGenerator` already documents), stored as SHA-256 — a database read cannot resume
  anyone's session.
- Token TTL = `expires_at` + 5 min grace. Pre-session token TTL 10 min, carries only `registration_id` and
  cannot reach any problem, draft, run or submit endpoint.
- **One valid session per attempt** (decision 1). A new exchange mints a new token and revokes the previous
  one, so two people sharing a code cannot work in parallel — the second entry evicts the first.
  `session_takeovers` is counted and shown on the admin roster, so sharing is *visible* rather than merely
  blocked, and a student whose browser crashes simply retypes their code and resumes.

**Rate limiting** — `/api/arena/access` 5/min/IP and 10/hour/code; run 1 per 4 s per attempt with a hard
cap per attempt; drafts 1/s per problem. The `VerificationController` javadoc already flags proxy-level
rate limiting as the deployment requirement; the arena endpoints join that list.

**Data exposure**
- `ProblemView` is assembled by a mapper that reads only the public half of a bank record — it cannot carry
  `corrected_code`, `bugs`, or a hidden test. Structural, not vigilance.
- Run results return visible-test detail and a binary verdict. **No hidden-test count reaches the browser
  in any payload, and no setting can produce one** (decision 5). Execution stdout/stderr is truncated to
  4 KB and scrubbed of file paths, so a `printf` loop cannot be used to dump a harness.
- The bank's `corrected_code`, `bugs` and `hidden_tests` live only in a JAR resource, never in PostgreSQL,
  so a database compromise does not expose them.
- No score, no rank, no per-test breakdown reaches a participant at any point.
- `/api/arena/access` keeps the existing one-refusal discipline: invalid, wrong event, and not-a-debugging
  code are indistinguishable.

**Execution isolation** — untrusted student code never runs in the Spring JVM or on its host.
**Self-hosted Judge0** (decision 2) in a container with **no network**, 2 s CPU, 256 MB, 64 KB output, no
filesystem writes outside a scratch dir, one process tree killed on timeout. `CodeExecutionEngine` is an
interface precisely so the
engine can be swapped or disabled without touching arena logic, and `DisabledExecutionEngine` returns a
clean `EXECUTION_UNAVAILABLE` rather than pretending.

**Integrity**
- Server clock only. `expires_at` is stamped at attempt start and never recomputed; changing the configured
  duration mid-event affects future attempts only.
- Expiry enforced twice: lazily on every request, and by a 60 s `@Scheduled` sweep so the admin roster and
  the scoring set are correct even for someone who closed their laptop.
- Final scoring re-runs the **stored drafts** server-side. Nothing the client reported during the mission
  is an input to the score.
- Every admin arena action is audited with actor, target and reason.

**Database** — all new tables get the V5 treatment: `ENABLE ROW LEVEL SECURITY` with no policies, and the
guarded `REVOKE` from `anon` / `authenticated`. `arena_attempt` holds drafts and `debug_problem` holds
reference solutions; both are strictly worse to leak than the participant table.

---

## Implementation architecture

### Migrations (V12 → V15)

There is **no problem-bank migration**. The bank is the five JSON resources, loaded and validated at
startup (correction 4) — so nothing below stores a reference solution or a hidden test.

```sql
-- V12  arena control. Keyed by event_id, not a singleton, so a second
--      terminal-run event later is data rather than a second mechanism —
--      the same reasoning as event.requires_access_code in V9.
arena_control(event_id PK → event, status, duration_seconds DEFAULT 2700,
              opened_at, ended_at, updated_at, updated_by)

-- V13  attempts.  problem_id is the bank's own string id ('C++-E-001'),
--      not a foreign key: the bank lives in a resource, not a table.
arena_attempt(id PK, registration_id UNIQUE → registration, participant_id,
              language, state, session_token_hash, session_issued_at,
              session_takeovers, started_at, expires_at, finalized_at,
              final_state_reason, score, solved_count, last_seen_at)
attempt_problem(id PK, attempt_id → arena_attempt, problem_id TEXT,
                ref, difficulty, ordinal, status, draft_code, draft_revision,
                run_count, best_visible_passed, solved_at,
                final_passed, final_total,
                UNIQUE(attempt_id, ref), UNIQUE(attempt_id, problem_id))

-- V14  telemetry + audit.  Hidden-test counts ARE recorded here: this table is
--      admin-only and never projected into a participant response. Decision 5
--      governs what leaves the server, not what the judge records.
arena_run(id PK, attempt_problem_id, requested_at, verdict, compile_ok,
          visible_passed, visible_total, hidden_passed, hidden_total, duration_ms)
arena_audit(id PK, actor, action, target_type, target_id, reason, created_at)

-- every one of the above: ENABLE ROW LEVEL SECURITY, guarded REVOKE from
-- anon/authenticated, exactly as V5 does.
```

### Backend wiring

`ArenaTokenFilter` registers **before** `BasicAuthenticationFilter` and only acts on `/api/arena/**` with a
`Bearer` header. `SecurityConfig` gains two lines and keeps its shape:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/arena/status", "/api/arena/access").permitAll()
.requestMatchers("/api/arena/**").hasRole("ARENA_PARTICIPANT")
.anyRequest().permitAll()
```

Admin arena endpoints live under `/api/admin/arena/**` and are therefore already protected by the existing
prefix rule — the property `AdminController`'s javadoc calls out as the reason a new endpoint is protected
by default rather than by someone remembering.

### Frontend wiring

- **Editor: CodeMirror 6**, not Monaco. ~180 KB gzipped against Monaco's ~900 KB+, it themes cleanly from
  the existing `@theme` tokens, and it behaves on tablets. For C / C++ / Java / Python / JS the practical
  difference is bracket matching and indentation, which CodeMirror does well. Language modes are
  dynamically imported per selection, so a Python participant never downloads the C++ grammar.
  New deps: `@codemirror/state`, `@codemirror/view`, `@codemirror/commands`, `@codemirror/lang-cpp`,
  `-java`, `-python`, `-javascript`.
- **The whole `/arena` route is lazy (`React.lazy`)**, so the editor bundle never touches the registration
  site — which is still the priority path until the event.
- **Lenis must not run on `/arena`.** Smooth scroll hijacking inside a code editor is actively harmful;
  `ArenaRoute` sits outside `RootLayout` and `ExperienceRoute`, like `/admin` already does.
- **Live updates: polling, not WebSocket.** `GET /api/arena/session` every 10 s plus on window focus.
  Thirty participants is 3 req/s. `useArenaHeartbeat` is written behind a transport interface so a
  WebSocket can replace it in CLAUDE.md's Phase 9 without touching a component.
- Clock skew: every response carries `serverTime`; the client stores `offset = serverTime − receivedAt` and
  renders `expiresAt − (Date.now() + offset)`. Re-derived on each heartbeat.
- `apiClient.ts`'s `KNOWN_CODES` set must gain the new codes, or every arena refusal degrades to a generic
  error toast.

### Build order

| Phase | Deliverable | Provable by |
|---|---|---|
| **A** | V12 + `ArenaControlService` + `/api/arena/status` + admin start/stop + S0 gate screen | Admin flips status; the gate screen flips within 10 s with nothing else built |
| **B** | Session tokens, `/access`, attempt start, S1–S4 | A real code reaches the briefing; a second device evicts the first |
| **C** | V13, `ProblemBankService` loading the 5 JSON banks (60 problems, fixed 12 per language), brief + editor + autosave | Refresh mid-mission restores code and clock exactly; startup fails loudly on a malformed bank |
| **D** | `CodeExecutionEngine` + Judge0 + RUN + rate limits + test dock | Runs return verdicts; hidden tests never appear in any payload |
| **E** | Final submit, server-side re-run, scoring (100/200/300, tiebreak on earlier submission time), S6–S8, expiry sweeper | Submit is irreversible; the clock expires an idle attempt |
| **F** | Admin arena console: roster, extend, terminate, reset, audit, results, CSV | An organiser can run the event from one screen |
| **G** | Animation timeline, responsive breakpoints, reduced motion, keyboard sheet | Full pass on a 1440, a 1024, a tablet, and with motion off |
| **H** | *Later* — Gemini generation behind the validation pipeline in CLAUDE.md | Generated problems pass validation or fall back to the curated bank |

A–C are the critical path: they are the parts that cannot be bought, borrowed, or degraded gracefully.
D can ship with `DisabledExecutionEngine` and a "RUN is offline" dock in the worst case, and the mission
still functions as a fix-and-submit event.

---

## Scoring, as locked

Points come from the bank, not from a constant in Java — every problem already carries
`points` of 100 / 200 / 300 matching its difficulty, so `ScoringService` reads the value rather than
re-deriving the ladder. Same reasoning as `event.registration_slot`: per-item policy is data.

```
score      = Σ points(p) for every problem whose FINAL server-side re-run passes all tests
maximum    = 4×100 + 4×200 + 4×300 = 2400
no partial credit — a problem counts only if every test passes
tiebreak   = earlier final submission time (arena_attempt.finalized_at)
```

An EXPIRED or TERMINATED attempt is scored identically from its last saved draft; its `finalized_at` is
the moment the deadline passed or the admin acted, so the tiebreak stays well-defined across all three
terminal states.

Deliberately absent: speed bonuses, streaks, penalties for failed runs, and anything else that would need
explaining to a first-year student disputing a result at a prize table.

## Remaining open items

None blocking. Two to settle before Phase D:

- Judge0 host sizing and the language-id mapping for the five runtimes.
- Whether failed-run count is shown to admins only (planned) or also used in post-event calibration.
