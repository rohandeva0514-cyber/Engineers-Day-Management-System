/** Event data hooks. The only way pages obtain events. */

import type { Event, EventId } from '@/domain/types';
import { fetchEvent, fetchEvents } from '@/services/eventsApi';
import { useAsync } from './useAsync';

export function useEvents() {
  return useAsync<Event[]>((signal) => fetchEvents(signal), []);
}

export function useEvent(eventId: EventId | undefined) {
  return useAsync<Event>(
    (signal) => {
      if (eventId === undefined) return Promise.reject(new Error('missing eventId'));
      return fetchEvent(eventId, signal);
    },
    [eventId],
  );
}
