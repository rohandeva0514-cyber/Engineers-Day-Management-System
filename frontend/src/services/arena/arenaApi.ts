/**
 * The arena's typed endpoints.
 *
 * Goes through the shared `apiRequest`, so the arena inherits the one HTTP
 * boundary the rest of the application already has: one timeout policy, one
 * `ApiError` shape, one place that calls `fetch`.
 *
 * As the arena grows a session, this module becomes the only place that attaches
 * the bearer token — components and hooks never build a request themselves.
 */

import { apiRequest } from '@/services/apiClient';
import type {
  AccessResponse,
  ArenaStatusSnapshot,
  AttemptStateResponse,
  DraftSaved,
  ProblemBoard,
  ProblemDetail,
  RunResult,
  SubmitResult,
} from './arenaTypes';

/**
 * `GET /api/arena/status`
 *
 * The gate. Cheap by design and polled every ten seconds by every waiting
 * participant, so nothing expensive may ever be added to it.
 *
 * A shorter timeout than the default: this call has a fresh attempt ten seconds
 * behind it, so hanging on for fifteen seconds would only stack requests on a
 * flaky hall network. Failing fast and retrying on the next tick is the better
 * behaviour here.
 */
export function fetchArenaStatus(signal?: AbortSignal): Promise<ArenaStatusSnapshot> {
  return apiRequest<ArenaStatusSnapshot>('/arena/status', {
    timeoutMs: 8_000,
    ...(signal ? { signal } : {}),
  });
}

/**
 * `POST /api/arena/access` — exchange an access code for a session.
 *
 * A POST with the code in the body, never a GET with it in a query string. The
 * response carries a session token, and neither the code nor the token has any
 * business in browser history or a proxy log.
 *
 * Safe to call repeatedly: the backend resumes the existing attempt and mints a
 * fresh token, which is what makes a crashed browser recoverable.
 */
export function checkIn(code: string): Promise<AccessResponse> {
  return apiRequest<AccessResponse>('/arena/access', {
    method: 'POST',
    body: { code: code.trim().toUpperCase() },
  });
}

/**
 * `GET /api/arena/attempt` — rehydrate.
 *
 * The single call that decides which screen to render. Everything the UI believes
 * about phase, language lock and remaining time comes from here.
 */
export function fetchAttemptState(
  token: string,
  signal?: AbortSignal,
): Promise<AttemptStateResponse> {
  return apiRequest<AttemptStateResponse>('/arena/attempt', {
    bearerToken: token,
    ...(signal ? { signal } : {}),
  });
}

/**
 * `PUT /api/arena/attempt/language` — record a tentative choice.
 *
 * Persisted rather than held in component state so a refresh restores the screen
 * the participant was actually on. Refused once the mission has started.
 */
export function chooseLanguage(token: string, language: string): Promise<AttemptStateResponse> {
  return apiRequest<AttemptStateResponse>('/arena/attempt/language', {
    method: 'PUT',
    body: { language },
    bearerToken: token,
  });
}

/**
 * `POST /api/arena/attempt/start` — begin the mission.
 *
 * The 45 minutes starts on the server, and the deadline comes back in the
 * response. The client never proposes one.
 */
export function startMission(token: string, language: string): Promise<AttemptStateResponse> {
  return apiRequest<AttemptStateResponse>('/arena/attempt/start', {
    method: 'POST',
    body: { language },
    bearerToken: token,
  });
}

/**
 * `POST /api/arena/attempt/submit` — final mission submission.
 *
 * Sends no body. The code being submitted is whatever autosave has already
 * persisted, and the timestamp is the server's — so there is nothing here a client
 * could misreport. Callers must flush any pending draft save and await it before
 * calling this, or the participant submits one debounce interval behind what they
 * can see on screen.
 *
 * One per attempt, irreversible.
 */
export function submitMission(token: string): Promise<AttemptStateResponse> {
  return apiRequest<AttemptStateResponse>('/arena/attempt/submit', {
    method: 'POST',
    bearerToken: token,
    timeoutMs: 30_000,
  });
}

/* ---------------------------------------------------------------- workspace */

/**
 * `GET /api/arena/problems` — the board.
 *
 * Note the absence of a language parameter. The server takes it from the attempt,
 * so there is no way for this client to ask for a bank it is not locked to — and
 * nothing here would be trusted if there were.
 */
export function fetchProblemBoard(token: string, signal?: AbortSignal): Promise<ProblemBoard> {
  return apiRequest<ProblemBoard>('/arena/problems', {
    bearerToken: token,
    ...(signal ? { signal } : {}),
  });
}

/**
 * `GET /api/arena/problems/{ref}` — one problem.
 *
 * `ref` is a board handle (`E-01`), not a bank id. Encoded anyway: a path segment
 * built from a server-supplied value should never be concatenated raw.
 */
export function fetchProblem(
  token: string,
  ref: string,
  signal?: AbortSignal,
): Promise<ProblemDetail> {
  return apiRequest<ProblemDetail>(`/arena/problems/${encodeURIComponent(ref)}`, {
    bearerToken: token,
    ...(signal ? { signal } : {}),
  });
}

/**
 * `PUT /api/arena/problems/{ref}/draft` — autosave.
 *
 * `revision` is the optimistic guard: the server refuses the write if this problem
 * has been saved elsewhere since we loaded it, rather than silently discarding the
 * other device's work.
 */
export function saveDraft(
  token: string,
  ref: string,
  code: string,
  revision: number,
): Promise<DraftSaved> {
  return apiRequest<DraftSaved>(`/arena/problems/${encodeURIComponent(ref)}/draft`, {
    method: 'PUT',
    body: { code, revision },
    bearerToken: token,
  });
}

/**
 * `POST /api/arena/problems/{ref}/run` — execute against the visible tests.
 *
 * The body carries the source and nothing else. There is deliberately nowhere to
 * put a language, a runtime, a time limit or a memory limit: the server derives the
 * language from the attempt and owns every sandbox parameter. Sending them would
 * change nothing, which is the point.
 *
 * A longer timeout than the default, because this waits on a compiler. Still
 * bounded — the backend gives up before this does.
 */
export function runCode(token: string, ref: string, code: string): Promise<RunResult> {
  return apiRequest<RunResult>(`/arena/problems/${encodeURIComponent(ref)}/run`, {
    method: 'POST',
    body: { code },
    bearerToken: token,
    timeoutMs: 45_000,
  });
}

/**
 * `POST /api/arena/problems/{ref}/submit` — judge this problem, finally.
 *
 * Irreversible for this problem, and the only path that can mark it solved. It does
 * not end the mission: the other eleven problems are untouched.
 */
export function submitProblem(token: string, ref: string, code: string): Promise<SubmitResult> {
  return apiRequest<SubmitResult>(`/arena/problems/${encodeURIComponent(ref)}/submit`, {
    method: 'POST',
    body: { code },
    bearerToken: token,
    timeoutMs: 60_000,
  });
}
