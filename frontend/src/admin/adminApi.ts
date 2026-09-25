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
 */

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
  registrationSlot: 'PRIMARY' | 'OPEN';
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

  const response = await fetch(`/api/admin${path}`, {
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

export function fetchParticipants(filters: ParticipantFilters): Promise<AdminParticipantPage> {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') search.set(key, value);
  }
  const query = search.toString();
  return adminRequest<AdminParticipantPage>(`/participants${query === '' ? '' : `?${query}`}`);
}
