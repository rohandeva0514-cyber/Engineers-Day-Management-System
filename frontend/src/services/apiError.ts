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
  /** The requested Debugging Arena lifecycle move is not one the state machine allows. */
  | 'ARENA_TRANSITION_INVALID'
  /** The arena has not been opened, so nobody may check in or start. */
  | 'ARENA_OFFLINE'
  /** Mission control has ended the arena. */
  | 'ARENA_ENDED'
  /** No live arena session — absent, lapsed, or displaced by another device. */
  | 'ARENA_SESSION_INVALID'
  /** The language was fixed when the mission started. */
  | 'ARENA_LANGUAGE_LOCKED'
  | 'ATTEMPT_ALREADY_STARTED'
  | 'ATTEMPT_ALREADY_FINALIZED'
  | 'LANGUAGE_NOT_SUPPORTED'
  | 'ATTEMPT_NOT_STARTED'
  | 'PROBLEM_NOT_FOUND'
  | 'DRAFT_STALE'
  | 'DRAFT_TOO_LARGE'
  /** This problem has already been judged and is final. Per-problem, not the mission. */
  | 'PROBLEM_ALREADY_SUBMITTED'
  /**
   * The judge failed — NOT a verdict on the participant's code.
   *
   * Must never be rendered as a wrong answer. Nothing was recorded, no status
   * moved, the draft is intact, and retrying is the correct response.
   */
  | 'EXECUTION_UNAVAILABLE'
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
  ARENA_TRANSITION_INVALID: 'That arena change is not allowed',
  ARENA_OFFLINE: 'The arena has not started',
  ARENA_ENDED: 'The arena has closed',
  ARENA_SESSION_INVALID: 'Your session has ended',
  ARENA_LANGUAGE_LOCKED: 'Your language is locked',
  ATTEMPT_ALREADY_STARTED: 'Your mission is already running',
  ATTEMPT_ALREADY_FINALIZED: 'Your mission is already over',
  LANGUAGE_NOT_SUPPORTED: 'That language is not available',
  ATTEMPT_NOT_STARTED: 'Your mission has not started',
  PROBLEM_NOT_FOUND: 'No such problem in your mission',
  DRAFT_STALE: 'This problem changed elsewhere',
  DRAFT_TOO_LARGE: 'That is too large to save',
  PROBLEM_ALREADY_SUBMITTED: 'Already submitted',
  EXECUTION_UNAVAILABLE: 'Execution service unavailable',
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
  ARENA_TRANSITION_INVALID:
    'An ended arena has to be reopened to Offline before it can be started again. That is deliberate — restarting a finished competition should not be one click.',
  ARENA_OFFLINE: 'Wait for the event administrator to open the arena. This screen updates on its own.',
  ARENA_SESSION_INVALID:
    'Enter your access code again. Your attempt and your remaining time are unaffected.',
  ARENA_LANGUAGE_LOCKED:
    'Your language was fixed when your mission started, so that the problems you are scored on cannot change part-way through.',
  EXECUTION_UNAVAILABLE:
    'This is a problem with our judge, not with your code. Nothing was recorded and your work is safe — try again in a moment.',
  PROBLEM_ALREADY_SUBMITTED:
    'This problem has been judged and cannot be changed. Your other problems are unaffected.',
  DRAFT_STALE:
    'This problem was saved on another device since you opened it. Reload the problem to see the current code.',
};
