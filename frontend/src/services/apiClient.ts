/**
 * The HTTP boundary.
 *
 * This is the only module in the public application that calls `fetch`. Components,
 * pages and hooks talk to the typed endpoint modules in this folder; nothing
 * reaches past them to the network. (The admin panel has its own client, for the
 * reasons given there; it shares this one's base URL via `apiBaseUrl.ts`.)
 *
 * Its second job is turning every possible failure — a 422, a 502, an unplugged
 * network cable, an HTML error page from a misconfigured proxy — into one
 * `ApiError` with a code the UI can branch on. Callers never see a raw Response,
 * never read a status code, and never see a stack trace.
 */

import { API_BASE_URL } from './apiBaseUrl';
import { ApiError, type ApiErrorBody, type ApiErrorCode } from './apiError';

/** Requests should fail visibly rather than hang a form open indefinitely. */
const DEFAULT_TIMEOUT_MS = 15_000;

const KNOWN_CODES = new Set<string>([
  'VALIDATION_FAILED',
  'MALFORMED_REQUEST',
  'EVENT_NOT_FOUND',
  'PARTICIPANT_NOT_FOUND',
  'ACCESS_CODE_INVALID',
  'REGISTRATION_CLOSED',
  'CAPACITY_FULL',
  'DUPLICATE_REGISTRATION',
  'EVENT_SLOT_ALREADY_TAKEN',
  'TEAM_NAME_TAKEN',
  'PARTICIPANT_IDENTITY_CONFLICT',
  'INELIGIBLE_YEAR',
  'INVALID_TEAM_SIZE',
  'SOLO_EVENT_REJECTS_TEAM',
  'TEAM_NAME_REQUIRED',
  'DUPLICATE_PARTICIPANT_IN_ROSTER',
  'ARENA_TRANSITION_INVALID',
  'ARENA_OFFLINE',
  'ARENA_ENDED',
  'ARENA_SESSION_INVALID',
  'ARENA_LANGUAGE_LOCKED',
  'ATTEMPT_ALREADY_STARTED',
  'ATTEMPT_ALREADY_FINALIZED',
  'LANGUAGE_NOT_SUPPORTED',
  'ATTEMPT_NOT_STARTED',
  'PROBLEM_NOT_FOUND',
  'DRAFT_STALE',
  'DRAFT_TOO_LARGE',
  'PROBLEM_ALREADY_SUBMITTED',
  'EXECUTION_UNAVAILABLE',
  'INTERNAL_ERROR',
]);

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT';
  body?: unknown;
  signal?: AbortSignal;
  timeoutMs?: number;
  /**
   * Arena session token, sent as `Authorization: Bearer`.
   *
   * Attached here rather than by the calling module so that this stays the only
   * place in the public application that builds a request. It is never put in a
   * path or a query string: URLs reach browser history, proxy logs and referrer
   * headers, and this value authorises a whole mission.
   */
  bearerToken?: string;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    method = 'GET',
    body,
    signal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    bearerToken,
  } = options;

  // Own timeout, combined with any caller abort (route change, unmount).
  const timeoutController = new AbortController();
  const timer = setTimeout(() => timeoutController.abort(), timeoutMs);
  const combinedSignal = signal
    ? AbortSignal.any([signal, timeoutController.signal])
    : timeoutController.signal;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(bearerToken === undefined ? {} : { Authorization: `Bearer ${bearerToken}` }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: combinedSignal,
    });
  } catch (cause) {
    // A caller-initiated abort is not an error the user should ever see — it means
    // they navigated away. Re-throw it so callers can recognise and ignore it.
    if (signal?.aborted) throw cause;

    throw new ApiError(
      timeoutController.signal.aborted ? 'UNAVAILABLE' : 'NETWORK',
      timeoutController.signal.aborted
        ? 'The registration service did not respond in time.'
        : 'Could not reach the registration service.',
    );
  } finally {
    clearTimeout(timer);
  }

  if (response.status === 204) return undefined as T;

  const payload = await readBody(response);

  if (!response.ok) throw toApiError(response, payload);

  if (payload === null || typeof payload !== 'object') {
    throw new ApiError('UNAVAILABLE', 'The registration service returned an unexpected response.', response.status);
  }
  return payload as T;
}

/** Parses JSON defensively — a proxy or container can return HTML on failure. */
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text().catch(() => '');
  if (text.trim() === '') return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

function toApiError(response: Response, payload: unknown): ApiError {
  const body = (payload ?? {}) as ApiErrorBody;

  // A body we recognise: the backend spoke, and it told us exactly what was wrong.
  if (typeof body.code === 'string' && KNOWN_CODES.has(body.code)) {
    return new ApiError(
      body.code as ApiErrorCode,
      body.message ?? 'The request was refused.',
      response.status,
      body.details ?? {},
    );
  }

  // Something answered but it was not our API — a gateway, a dev proxy with no
  // backend behind it, a 502. Treat it as unavailable rather than inventing a
  // domain meaning for a status code we did not produce.
  if (response.status >= 500 || response.status === 0) {
    return new ApiError('UNAVAILABLE', 'The registration service is unavailable.', response.status);
  }
  // A 404 with no recognised code in the body is not a missing EVENT - it is an
  // endpoint the server does not have. Mapping it to EVENT_NOT_FOUND used to put
  // "Event not found" in front of a participant whose submission had hit a backend
  // that predated the endpoint, which sent them looking in exactly the wrong place.
  //
  // Real event 404s are unaffected: the backend always sends `code`, so they take the
  // KNOWN_CODES branch above and never reach here.
  if (response.status === 404) {
    return new ApiError('UNAVAILABLE',
      'That request did not reach the service. It may be running an older version — '
      + 'tell an organiser.', 404);
  }
  return new ApiError('UNKNOWN', body.message ?? 'The request was refused.', response.status);
}

/** Query string builder that omits empty values. */
export function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  const rendered = search.toString();
  return rendered === '' ? '' : `?${rendered}`;
}
