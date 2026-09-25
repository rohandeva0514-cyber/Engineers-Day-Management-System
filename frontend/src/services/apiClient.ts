/**
 * The HTTP boundary.
 *
 * This is the only module in the application that calls `fetch`. Components,
 * pages and hooks talk to the typed endpoint modules in this folder; nothing
 * reaches past them to the network.
 *
 * Its second job is turning every possible failure — a 422, a 502, an unplugged
 * network cable, an HTML error page from a misconfigured proxy — into one
 * `ApiError` with a code the UI can branch on. Callers never see a raw Response,
 * never read a status code, and never see a stack trace.
 */

import { ApiError, type ApiErrorBody, type ApiErrorCode } from './apiError';

/**
 * In development this is `/api`, proxied to Spring Boot by Vite, so the browser
 * sees a same-origin request and CORS never enters the picture. In production set
 * `VITE_API_BASE_URL` to wherever the service is deployed.
 */
const BASE_URL = import.meta.env['VITE_API_BASE_URL'] ?? '/api';

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
  'PRIMARY_EVENT_ALREADY_TAKEN',
  'TEAM_NAME_TAKEN',
  'PARTICIPANT_IDENTITY_CONFLICT',
  'INELIGIBLE_YEAR',
  'INVALID_TEAM_SIZE',
  'SOLO_EVENT_REJECTS_TEAM',
  'TEAM_NAME_REQUIRED',
  'DUPLICATE_PARTICIPANT_IN_ROSTER',
  'INTERNAL_ERROR',
]);

interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  signal?: AbortSignal;
  timeoutMs?: number;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, timeoutMs = DEFAULT_TIMEOUT_MS } = options;

  // Own timeout, combined with any caller abort (route change, unmount).
  const timeoutController = new AbortController();
  const timer = setTimeout(() => timeoutController.abort(), timeoutMs);
  const combinedSignal = signal
    ? AbortSignal.any([signal, timeoutController.signal])
    : timeoutController.signal;

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
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
  if (response.status === 404) {
    return new ApiError('EVENT_NOT_FOUND', 'That resource does not exist.', 404);
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
