/**
 * Event discovery endpoints.
 *
 * Both return the backend's *effective* registration status, with SOLD_OUT already
 * folded in from live seat counts, so nothing downstream has to work it out.
 */

import type { Event, EventId } from '@/domain/types';
import { apiRequest } from './apiClient';

/** `GET /api/events` — all seven, in display order. */
export function fetchEvents(signal?: AbortSignal): Promise<Event[]> {
  return apiRequest<Event[]>('/events', signal ? { signal } : {});
}

/** `GET /api/events/{eventId}` — the id is a slug and is matched case-insensitively. */
export function fetchEvent(eventId: EventId, signal?: AbortSignal): Promise<Event> {
  return apiRequest<Event>(`/events/${encodeURIComponent(eventId)}`, signal ? { signal } : {});
}
