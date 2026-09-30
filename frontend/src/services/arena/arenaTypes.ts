/**
 * The Debugging Arena's wire types.
 *
 * Kept apart from `domain/types.ts` deliberately. That module describes
 * registration — a student choosing events weeks before the event. This one
 * describes a live competition, and the two share no concepts beyond the
 * participant. Folding them together would mean the registration bundle carried
 * arena types it never uses, and a reviewer reading either would have to work out
 * which half applied.
 *
 * Every type here mirrors the Spring Boot wire format exactly. The backend remains
 * authoritative for every decision; nothing in the arena UI is a substitute for a
 * server check.
 */

/**
 * The arena's lifecycle, as the server reports it.
 *
 * - `OFFLINE` — no check-in. Either it has not started, or an organiser paused it.
 * - `ACTIVE` — the competition is running.
 * - `ENDED` — the event is over and remaining attempts have been finalized.
 *
 * The client never derives this. It is polled, and it is the only thing that
 * decides whether the arena is reachable — a deployed page is not an open arena.
 */
export type ArenaStatus = 'OFFLINE' | 'ACTIVE' | 'ENDED';

/** One language a participant may choose. Sent by the server, never hardcoded here. */
export interface ArenaLanguageOption {
  /** Stable wire identifier, e.g. `cpp`. */
  id: string;
  /** Display name, e.g. `C++`. */
  label: string;
  /** Runtime shown on the language card, e.g. `G++ 13`. */
  runtime: string;
}

/**
 * `GET /api/arena/status` — the gate.
 *
 * Public and polled, so it carries only what a closed door needs to say. No
 * participant counts, no attempt state, nothing about who is running.
 */
export interface ArenaStatusSnapshot {
  eventId: string;
  status: ArenaStatus;
  /**
   * The server's view of now, ISO-8601.
   *
   * Every countdown the arena renders is anchored to this rather than to
   * `Date.now()`, so a laptop with a badly-set clock does not show a deadline that
   * has not arrived. The client timer is decoration regardless: the backend
   * refuses late requests whatever the browser believes.
   */
  serverTime: string;
  /** Mission length for attempts started from now on. 2700 = 45 minutes. */
  durationSeconds: number;
  languages: ArenaLanguageOption[];
}

/* ------------------------------------------------------------------ attempt */

/**
 * Where a participant is in their run.
 *
 * - `INITIALIZED` — checked in, clock **not** started, language still changeable.
 * - `ACTIVE` — mission running.
 * - `SUBMITTED` / `EXPIRED` / `TERMINATED` — over, and not re-enterable.
 *
 * Derived entirely from the server. The client never computes this, not even
 * expiry: an attempt past its deadline is reported as `EXPIRED` by the backend, so
 * a browser with a wrong clock cannot decide it is still running.
 */
export type AttemptState =
  | 'INITIALIZED'
  | 'ACTIVE'
  | 'SUBMITTED'
  | 'EXPIRED'
  | 'TERMINATED';

/**
 * Who the system says is at the terminal.
 *
 * Five fields, so a student can confirm "this is my registration" and no more.
 * There is deliberately no email, phone, roll number or internal id — the arena
 * session is held by whoever typed the code.
 */
export interface ParticipantIdentity {
  fullName: string;
  branch: string | null;
  division: string | null;
  yearLevel: number;
  eventName: string;
}

/** The attempt as its own participant may see it. Never a score. */
export interface AttemptView {
  state: AttemptState;
  /** `null` until a language is chosen. */
  language: string | null;
  /** True once the mission has started. The server decides this, not the UI. */
  languageLocked: boolean;
  startedAt: string | null;
  /** The authoritative deadline. `null` until the mission starts. */
  expiresAt: string | null;
  remainingSeconds: number;
}

/** `POST /api/arena/access` — a successful check-in. */
export interface AccessResponse {
  sessionToken: string;
  sessionExpiresAt: string;
  serverTime: string;
  participant: ParticipantIdentity;
  attempt: AttemptView;
  languages: ArenaLanguageOption[];
}

/**
 * `GET /api/arena/attempt` — the single rehydrate call.
 *
 * One request restores the whole UI after a refresh: who you are, where you are,
 * how long is left, and whether the arena is still running.
 */
export interface AttemptStateResponse {
  serverTime: string;
  durationSeconds: number;
  arenaStatus: ArenaStatus;
  participant: ParticipantIdentity;
  attempt: AttemptView;
  languages: ArenaLanguageOption[];
}

/* ---------------------------------------------------------------- workspace */

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

/**
 * What a participant has done with one problem.
 *
 * `SOLVED` is server-recorded and unreachable until execution exists — a draft
 * save can only ever reach `ATTEMPTED`. The client never computes this.
 */
export type ProblemStatus = 'NOT_ATTEMPTED' | 'ATTEMPTED' | 'SOLVED';

/**
 * One tile on the board.
 *
 * Problems are addressed by `ref` — `E-01` through `H-04` — which is unique within
 * an attempt. The bank's own ids never reach the browser.
 */
export interface ProblemSummary {
  ref: string;
  title: string;
  difficulty: Difficulty;
  points: number;
  concepts: string[];
  status: ProblemStatus;
  hasDraft: boolean;
}

/** One difficulty zone. All three arrive open; there is no locked flag to render. */
export interface ProblemZone {
  difficulty: Difficulty;
  points: number;
  problems: ProblemSummary[];
}

/** `GET /api/arena/problems` */
export interface ProblemBoard {
  serverTime: string;
  language: string;
  remainingSeconds: number;
  expiresAt: string | null;
  solvedCount: number;
  totalProblems: number;
  zones: ProblemZone[];
}

/** A worked example shown with the problem. Hidden tests never appear here. */
export interface VisibleTest {
  input: string;
  expectedOutput: string;
}

/**
 * `GET /api/arena/problems/{ref}`
 *
 * Note what this interface cannot hold: corrected code, bug notes, hidden tests,
 * or a hidden-test count. The server has no field to put them in either.
 */
export interface ProblemDetail {
  serverTime: string;
  ref: string;
  title: string;
  difficulty: Difficulty;
  points: number;
  concepts: string[];
  problemStatement: string;
  inputFormat: string;
  outputFormat: string;
  constraints: string;
  language: string;
  /** The bank's starting code — what the participant is asked to fix. */
  buggyCode: string;
  /** What they have typed since, or null if untouched. */
  draftCode: string | null;
  draftRevision: number;
  status: ProblemStatus;
  visibleTests: VisibleTest[];
}

/** `PUT /api/arena/problems/{ref}/draft` */
export interface DraftSaved {
  ref: string;
  revision: number;
  status: ProblemStatus;
  savedAt: string;
}

/* ---------------------------------------------------------------- execution */

/**
 * A verdict, normalised by the backend away from any judge's vocabulary.
 *
 * The client never sees Judge0 status ids, submission tokens, or the judge's URL —
 * it does not know Judge0 exists. `INTERNAL_ERROR` is deliberately absent: the
 * backend converts an engine failure into a 503 `EXECUTION_UNAVAILABLE` rather
 * than a verdict, so it can never arrive here as a result.
 */
export type ExecutionStatus =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILATION_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED';

/** One visible test, as executed. Safe: the participant already has these. */
export interface TestOutcome {
  index: number;
  passed: boolean;
  status: string;
  input: string;
  expectedOutput: string;
  actualOutput: string | null;
  timeMs: number | null;
}

/**
 * `POST /api/arena/problems/{ref}/run`
 *
 * Visible tests only. `passedCount`/`totalCount` refer to those, which are already
 * on screen. There is no hidden count here and no field that could carry one.
 */
export interface RunResult {
  serverTime: string;
  ref: string;
  status: ExecutionStatus;
  passedCount: number;
  totalCount: number;
  tests: TestOutcome[];
  compileOutput: string | null;
  stderr: string | null;
  runCount: number;
  problemStatus: ProblemStatus;
}

/**
 * `POST /api/arena/problems/{ref}/submit`
 *
 * The hidden suite decides this, and the response describes none of its shape — no
 * test list, no counts, no ratio. A boolean and a verdict.
 */
export interface SubmitResult {
  serverTime: string;
  ref: string;
  status: ExecutionStatus;
  solved: boolean;
  submittedAt: string;
  compileOutput: string | null;
  problemStatus: ProblemStatus;
}
