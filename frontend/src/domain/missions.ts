/**
 * How the event catalogue is organised into a mission select.
 *
 * Pure functions over API data. No React, no DOM — so the grouping that decides
 * what a student sees for their year can be reasoned about, and checked, on its
 * own.
 *
 * The one rule this module enforces: **eligibility comes from the API**. A year
 * sees an event because the server said `eligibleYears` contains that year, not
 * because a list here says so. That is why FIX IT appears for both years and
 * Rapid Research for one — nothing in this file names either of them.
 *
 * The two years are grouped on different axes, because the programme announces
 * them differently:
 *
 *   FIRST YEAR   split by discipline — NON-TECH / TECH. Not derivable from the
 *                API, so it comes from `siteContent`'s presentation metadata.
 *
 *   SECOND YEAR  split by exclusivity — COMMON / EXCLUSIVE. Fully derivable: an
 *                event open to one year only is exclusive to it.
 */

import { identityFor } from '@/data/siteContent';
import type { Event, YearLevel } from './types';

export type TrackId = 'NON_TECH' | 'TECH' | 'COMMON' | 'EXCLUSIVE';

export interface Track {
  id: TrackId;
  label: string;
  /** One line explaining what the split means. Shown under the track heading. */
  note: string;
  missions: Mission[];
}

export interface Mission {
  event: Event;
  /** 1-based position within the whole year view, for the mission number. */
  index: number;
}

const TRACK_LABEL: Record<TrackId, { label: string; note: string }> = {
  NON_TECH: { label: 'Non-tech', note: 'Open ground. No code required.' },
  TECH: { label: 'Tech', note: 'Build, break, debug and pitch.' },
  COMMON: { label: 'Common', note: 'Shared with the first year.' },
  EXCLUSIVE: { label: 'Exclusive', note: 'Second year only.' },
};

/** Events this year can actually enter, as the server defines it. */
export function eventsForYear(events: readonly Event[], year: YearLevel): Event[] {
  return events.filter((event) => event.eligibleYears.includes(year));
}

/** True when an event is open to exactly one year — the server's own answer. */
function isExclusive(event: Event): boolean {
  return event.eligibleYears.length === 1;
}

function byRank(a: Event, b: Event): number {
  return identityFor(a.eventId).rank - identityFor(b.eventId).rank;
}

/**
 * The mission board for a year: ordered tracks, each with numbered missions.
 *
 * Mission numbers run continuously across the whole year view rather than
 * restarting per track, so "Mission 05" identifies one event unambiguously
 * within what the student is looking at.
 */
export function missionBoard(events: readonly Event[], year: YearLevel): Track[] {
  const eligible = eventsForYear(events, year);

  const order: TrackId[] =
    year === 1 ? ['NON_TECH', 'TECH'] : ['COMMON', 'EXCLUSIVE'];

  const bucket = (event: Event): TrackId =>
    year === 1
      ? identityFor(event.eventId).discipline
      : isExclusive(event)
        ? 'EXCLUSIVE'
        : 'COMMON';

  let counter = 0;

  return order
    .map((id) => {
      const missions = eligible
        .filter((event) => bucket(event) === id)
        .sort(byRank)
        .map((event) => ({ event, index: ++counter }));

      return { id, ...TRACK_LABEL[id], missions };
    })
    .filter((track) => track.missions.length > 0);
}

/**
 * Column span for a mission in the 12-column editorial grid.
 *
 * A repeating 7/5/5/7 rhythm, so rows alternate which side carries the wider
 * panel instead of settling into an even three-up grid. A track holding a
 * single mission gets the featured width rather than the full row — being set
 * apart should not read as being ranked above.
 */
export function missionSpan(indexInTrack: number, trackSize: number): number {
  if (trackSize === 1) return 7;
  return [7, 5, 5, 7][indexInTrack % 4] ?? 6;
}

/** The first mission of a track leads it, and is given more room to speak. */
export function isFeatured(indexInTrack: number): boolean {
  return indexInTrack === 0;
}
