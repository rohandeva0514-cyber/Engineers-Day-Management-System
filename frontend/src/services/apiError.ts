/**
 * The error taxonomy, mirrored from the backend's `ApiErrorCode`.
 *
 * Clients branch on `code`, never on status alone and never on message text. That
 * is what lets the registration UI show a real "BuildX just sold out" screen with
 * the right recovery action instead of a generic red toast.
 */

/** Codes the backend can return. `NETWORK`/`UNAVAILABLE`/`UNKNOWN` are client-side additions. */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'MALFORMED_REQUEST'
  | 'EVENT_NOT_FOUND'
  | 'PARTICIPANT_NOT_FOUND'
  | 'ACCESS_CODE_INVALID'
  | 'REGISTRATION_CLOSED'
  | 'CAPACITY_FULL'
  | 'DUPLICATE_REGISTRATION'
  | 'EVENT_SLOT_ALREADY_TAKEN'
  | 'TEAM_NAME_TAKEN'
  | 'PARTICIPANT_IDENTITY_CONFLICT'
  | 'INELIGIBLE_YEAR'
  | 'INVALID_TEAM_SIZE'
  | 'SOLO_EVENT_REJECTS_TEAM'
  | 'TEAM_NAME_REQUIRED'
  | 'DUPLICATE_PARTICIPANT_IN_ROSTER'
  | 'INTERNAL_ERROR'
  /** The request never reached the server. */
  | 'NETWORK'
  /** Reached something, but it was not the API — wrong port, proxy down, 502. */
  | 'UNAVAILABLE'
  | 'UNKNOWN';

/** The backend's error body. `details` is structured context, not prose. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  path?: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;
  readonly details: Record<string, unknown>;

  constructor(code: ApiErrorCode, message: string, status = 0, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.details = details;
  }

  /**
   * Per-field messages from a 400.
   *
   * The backend keys these by the request path — `participants[0].email` — which is
   * exactly how the roster form addresses its own fields, so they can be attached
   * to the right input without any translation.
   */
  get fieldErrors(): Record<string, string> {
    const raw = this.details['fieldErrors'];
    if (raw === null || typeof raw !== 'object') return {};

    const out: Record<string, string> = {};
    for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
      if (typeof value === 'string') out[key] = value;
    }
    return out;
  }

  /** Roll numbers the server named — duplicates, or the ineligible ones. */
  get rollNos(): string[] {
    const raw = this.details['rollNos'];
    return Array.isArray(raw) ? raw.filter((v): v is string => typeof v === 'string') : [];
  }

  /** True when retrying the identical request could plausibly succeed later. */
  get isTransient(): boolean {
    return this.code === 'NETWORK' || this.code === 'UNAVAILABLE' || this.code === 'INTERNAL_ERROR';
  }

  /**
   * True when the event's state, not the submitted data, caused the refusal.
   * These want "go back to the event" rather than "fix this field".
   */
  get isEventStateProblem(): boolean {
    return (
      this.code === 'REGISTRATION_CLOSED' ||
      this.code === 'CAPACITY_FULL' ||
      this.code === 'EVENT_NOT_FOUND'
    );
  }
}

/**
 * Headline text shown to a student.
 *
 * The backend's own `message` is written to be shown and is usually more specific
 * (it names the roll numbers, the required team size). These are the fallbacks and
 * the wording for the client-side codes it cannot produce.
 */
export const ERROR_TITLES: Record<ApiErrorCode, string> = {
  VALIDATION_FAILED: 'Check the highlighted fields',
  MALFORMED_REQUEST: 'That request could not be read',
  EVENT_NOT_FOUND: 'Event not found',
  PARTICIPANT_NOT_FOUND: 'No registration found',
  ACCESS_CODE_INVALID: 'Code not recognised',
  REGISTRATION_CLOSED: 'Registration is closed',
  CAPACITY_FULL: 'This event is full',
  DUPLICATE_REGISTRATION: 'Already registered',
  EVENT_SLOT_ALREADY_TAKEN: 'You already have an event in this group',
  TEAM_NAME_TAKEN: 'That team name is taken',
  PARTICIPANT_IDENTITY_CONFLICT: 'Those details do not match our records',
  INELIGIBLE_YEAR: 'Not eligible for this event',
  INVALID_TEAM_SIZE: 'Team size is not valid',
  SOLO_EVENT_REJECTS_TEAM: 'This is a solo event',
  TEAM_NAME_REQUIRED: 'A team name is required',
  DUPLICATE_PARTICIPANT_IN_ROSTER: 'Someone is listed twice',
  INTERNAL_ERROR: 'Something went wrong on our side',
  NETWORK: 'No connection',
  UNAVAILABLE: 'Registration service unavailable',
  UNKNOWN: 'Unexpected error',
};

/** What the student can actually do about it. */
export const ERROR_HINTS: Partial<Record<ApiErrorCode, string>> = {
  NETWORK: 'Check your internet connection and try again. Nothing was submitted.',
  UNAVAILABLE: 'The service is not responding. Nothing was submitted — please try again shortly.',
  INTERNAL_ERROR: 'Nothing was saved. Please try again in a moment.',
  CAPACITY_FULL: 'The last seats were taken while your request was being processed.',
  REGISTRATION_CLOSED: 'Registration for this event is no longer being accepted.',
  PARTICIPANT_IDENTITY_CONFLICT:
    'This roll number is already on record with a different email address or year. Use the same details you registered with, or contact the organisers.',
  DUPLICATE_REGISTRATION: 'You can only register once per event.',
  EVENT_SLOT_ALREADY_TAKEN:
    'Each student takes one event from each group. You are already registered for another event in this one — your other groups are unaffected.',
  TEAM_NAME_TAKEN: 'Pick a different team name and submit again.',
};
