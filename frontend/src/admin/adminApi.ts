/**
 * The admin API client.
 *
 * Separate from `services/` on purpose: everything here requires credentials and
 * carries participant contact details, and keeping it in its own module means a
 * public page cannot import an admin call by autocomplete accident.
 *
 * Credentials are held in memory for the session and sent as HTTP Basic. They are
 * deliberately NOT written to localStorage — a persisted admin password on a
 * shared operations laptop outlives the person using it.
 *
 * The backend it talks to is `API_BASE_URL`, the same value the public client uses,
 * rather than a relative `/api/admin`. A relative path resolves against whatever
 * origin served the page, so once the frontend and the API were deployed to
 * different hosts the panel asked the static host for its dashboard and got the
 * single-page app's own 404 back. The dev proxy hid it, because there the two
 * origins really are one.
 */

import { API_BASE_URL } from '@/services/apiBaseUrl';
import { ApiError } from '@/services/apiError';

export interface AdminEventRow {
  eventId: string;
  name: string;
  status: 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'SOLD_OUT';
  storedStatus: 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'SOLD_OUT';
  closedByCapacity: boolean;
  registrations: number;
  seatsTaken: number | null;
  capacity: number | null;
  capacityUnit: string;
  participationType: string;
  registrationSlot: 'CORE' | 'BUILD' | 'CHALLENGE';
  eligibleYears: number[];
}

export interface AdminOverview {
  totalParticipants: number;
  totalRegistrations: number;
  openEvents: number;
  closedEvents: number;
  registrationSystemOpen: boolean;
}

export interface AdminDashboard {
  overview: AdminOverview;
  events: AdminEventRow[];
}

export interface AdminParticipantRow {
  registrationId: number;
  registeredAt: string;
  eventId: string;
  eventName: string;
  participantId: number;
  fullName: string;
  email: string;
  rollNo: string;
  phone: string | null;
  branch: string | null;
  division: string | null;
  yearLevel: number;
  teamName: string | null;
  /** Event-day access code, or null for events that issue none. */
  accessCode: string | null;
}

export interface AdminParticipantPage {
  total: number;
  participants: AdminParticipantRow[];
}

export interface ParticipantFilters {
  eventId?: string;
  year?: string;
  branch?: string;
  division?: string;
  search?: string;
}

/** In-memory only. Cleared on reload, which is the intended lifetime. */
let credentials: string | null = null;

export function setAdminCredentials(username: string, password: string): void {
  credentials = btoa(`${username}:${password}`);
}

export function clearAdminCredentials(): void {
  credentials = null;
}

export function hasAdminCredentials(): boolean {
  return credentials !== null;
}

async function adminRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (credentials === null) {
    throw new ApiError('UNKNOWN', 'Not signed in.', 401);
  }

  const response = await fetch(`${API_BASE_URL}/admin${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Basic ${credentials}`,
      ...(init.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...init.headers,
    },
  });

  if (response.status === 401 || response.status === 403) {
    // Wrong or revoked credentials. Dropping them here means the panel returns to
    // the sign-in form rather than looping on failed requests.
    clearAdminCredentials();
    throw new ApiError('UNKNOWN', 'Sign-in failed. Check the admin password.', response.status);
  }

  const text = await response.text().catch(() => '');
  const body: unknown = text.trim() === '' ? null : JSON.parse(text);

  if (!response.ok) {
    const shaped = body as { message?: string } | null;
    throw new ApiError('UNKNOWN', shaped?.message ?? 'That request failed.', response.status);
  }
  return body as T;
}

export function fetchDashboard(): Promise<AdminDashboard> {
  return adminRequest<AdminDashboard>('/dashboard');
}

export function setEventStatus(
  eventId: string,
  status: 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED',
): Promise<AdminEventRow> {
  return adminRequest<AdminEventRow>(`/events/${encodeURIComponent(eventId)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function setSystemRegistration(open: boolean): Promise<{ registrationSystemOpen: boolean }> {
  return adminRequest<{ registrationSystemOpen: boolean }>('/registration/status', {
    method: 'PATCH',
    body: JSON.stringify({ open }),
  });
}

/* ------------------------------------------------------------- arena control */

export type ArenaStatus = 'OFFLINE' | 'ACTIVE' | 'ENDED';

/**
 * The arena switch as an organiser sees it.
 *
 * Richer than the public `/api/arena/status`, and that is the point: this one
 * carries who last moved the switch and when, which has no business on an
 * endpoint anyone on the internet polls.
 */
export interface ArenaControlView {
  eventId: string;
  status: ArenaStatus;
  durationSeconds: number;
  openedAt: string | null;
  endedAt: string | null;
  updatedAt: string;
  updatedBy: string | null;
}

export interface ArenaDashboard {
  control: ArenaControlView;
}

export function fetchArenaControl(): Promise<ArenaDashboard> {
  return adminRequest<ArenaDashboard>('/arena');
}

/**
 * Start, pause, or end the arena.
 *
 * The backend enforces the transition rules — an ended arena cannot jump straight
 * back to active. Hiding a button would not stop a direct call, so the button is a
 * convenience and the state machine is the enforcement.
 */
export function setArenaStatus(status: ArenaStatus): Promise<ArenaControlView> {
  return adminRequest<ArenaControlView>('/arena/status', {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

/* -------------------------------------------------- manual evaluation ----- */

export interface SubmissionRow {
  attemptId: number;
  fullName: string;
  rollNo: string;
  email: string;
  branch: string | null;
  division: string | null;
  yearLevel: number;
  language: string | null;
  state: string;
  /** Null when the participant's clock ran out without submitting. */
  finalSubmittedAt: string | null;
  totalScore: number | null;
  evaluationStatus: 'PENDING' | 'PARTIAL' | 'EVALUATED';
  problemsEvaluated: number;
  problemsTotal: number;
}

export interface SubmittedProblem {
  ref: string;
  title: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  points: number;
  status: string;
  attempted: boolean;
  /** The participant's autosaved code. Null if they never typed here. */
  sourceCode: string | null;
  awardedPoints: number | null;

  /**
   * What the problem asked for.
   *
   * Needed to judge the code at all — a title alone does not let anyone decide
   * whether a solution is correct. Null only if the bank entry could not be
   * resolved, which should not happen for a started attempt.
   */
  problemStatement: string | null;
  inputFormat: string | null;
  outputFormat: string | null;
  constraints: string | null;
  visibleTests: { input: string; expectedOutput: string }[];
}

export interface SubmissionDetail {
  participant: SubmissionRow;
  problems: SubmittedProblem[];
}

export function fetchSubmissions(): Promise<{ total: number; submissions: SubmissionRow[] }> {
  return adminRequest('/arena/submissions');
}

export function fetchSubmission(attemptId: number): Promise<SubmissionDetail> {
  return adminRequest(`/arena/submissions/${attemptId}`);
}

/**
 * Award 0 or the problem's full points. `null` clears the award.
 *
 * The backend refuses anything other than 0 or the full value, and the database
 * refuses it again — there is no partial credit to express.
 */
export function awardPoints(
  attemptId: number,
  ref: string,
  points: number | null,
): Promise<SubmissionDetail> {
  return adminRequest(`/arena/submissions/${attemptId}/problems/${encodeURIComponent(ref)}/award`, {
    method: 'PUT',
    body: JSON.stringify({ points }),
  });
}

/** The CSV export URL. Opened with credentials by the panel. */
export function submissionsExportUrl(): string {
  return `${API_BASE_URL}/admin/arena/submissions/export.csv`;
}

/**
 * Fetch the export with the in-memory admin credentials and hand back a blob URL.
 *
 * A plain link would not carry the Basic auth header, so the browser would prompt
 * or 401. Fetching it here reuses the credentials the panel already holds.
 */
export async function downloadSubmissionsCsv(): Promise<string> {
  if (credentials === null) throw new ApiError('UNKNOWN', 'Not signed in.', 401);

  const response = await fetch(submissionsExportUrl(), {
    headers: { Authorization: `Basic ${credentials}`, Accept: 'text/csv' },
  });
  if (!response.ok) {
    throw new ApiError('UNKNOWN', 'The export could not be generated.', response.status);
  }
  return URL.createObjectURL(await response.blob());
}

export function fetchParticipants(filters: ParticipantFilters): Promise<AdminParticipantPage> {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') search.set(key, value);
  }
  const query = search.toString();
  return adminRequest<AdminParticipantPage>(`/participants${query === '' ? '' : `?${query}`}`);
}
