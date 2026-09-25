/**
 * Event discovery endpoints.
 *
 * Both return the backend's *effective* registration status, with SOLD_OUT already
 * folded in from live seat counts, so nothing downstream has to work it out.
 */

import type { Event, EventId } from '@/domain/types';
import { apiRequest } from './apiClient';

/**
 * How long the public catalogue reads wait before giving up.
 *
 * The event service is only woken by a request. When it has been idle it has to
 * start before it can answer, and the first response of the day can take well over
 * a minute; the default 15s ceiling turned that healthy-but-waking service into
 * "Registration service unavailable" while it was still on its way up.
 *
 * This raises the ceiling, it does not add waiting: a warm service still answers in
 * milliseconds, and a genuine failure — DNS, refused connection, 500 — rejects
 * immediately because fetch never reaches the timeout.
 *
 * Scoped to these two GETs on purpose. It is NOT the app-wide default, and
 * registration POSTs keep the short one: a student who has browsed the catalogue is
 * talking to a service that is already awake, and a submit button that can hang for
 * two minutes invites a double submission.
 */
export const CATALOGUE_TIMEOUT_MS = 120_000;

/** `GET /api/events` — all seven, in display order. */
export function fetchEvents(signal?: AbortSignal): Promise<Event[]> {
  return apiRequest<Event[]>('/events', {
    timeoutMs: CATALOGUE_TIMEOUT_MS,
    ...(signal ? { signal } : {}),
  });
}

/**
 * `GET /api/events/{eventId}` — the id is a slug and is matched case-insensitively.
 *
 * Shares the catalogue timeout: a shared link to one event is just as likely to be
 * the request that wakes the service as the grid is.
 */
export function fetchEvent(eventId: EventId, signal?: AbortSignal): Promise<Event> {
  return apiRequest<Event>(`/events/${encodeURIComponent(eventId)}`, {
    timeoutMs: CATALOGUE_TIMEOUT_MS,
    ...(signal ? { signal } : {}),
  });
}
