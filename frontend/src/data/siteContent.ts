/**
 * Static site copy and per-event visual identity.
 *
 * Content, not logic. Every *rule* — eligibility, team size, capacity, status —
 * comes from the API; this file only carries what the backend has no opinion
 * about: the landing-page copy, and the district colour each event will wear when
 * the cinematic layer is built.
 *
 * Keeping the identity map keyed by event id is what lets the events grid render
 * from API data with a lookup, instead of seven hardcoded blocks of JSX.
 */

import type { EventId } from '@/domain/types';

export const SITE = {
  org: 'MIT TECH KERNEL',
  event: "ENGINEERS' DAY 2026",
  tagline: 'Seven events. One day. Built by engineers, for engineers.',
  intro:
    'Seven events across both years — from over-the-board chess to a timed debugging sprint. Every student takes three: FIX IT, one build event and one challenge. Check what you are eligible for, assemble a team where one is needed, and claim your place.',
  registrationDeadlineLabel: '29 SEP 2026',
  /** IST. Used for the closing countdown in the hero readout. */
  registrationDeadline: '2026-09-29T23:59:59+05:30',

  /**
   * Event counts, for readouts and copy only.
   *
   * These are NOT rules. Eligibility and the actual catalogue come from
   * `GET /api/events`; these exist so the hero can print a mission count without
   * blocking its first paint on a network request. If they ever disagree with
   * the API, the API is right and these are stale copy.
   *
   *   first year  Chess, Tech Debate, FIX IT, Ideathon, BuildX, Debugging
   *   second year all of the above, plus Rapid Research
   */
  eventCount: 7,
  firstYearEventCount: 6,
  secondYearEventCount: 7,
} as const;

/**
 * Visual identity per event.
 *
 * `accent` is used sparingly — a rail, a label, a hover state — never as a fill.
 * `codename` is the district this event will occupy in the cinematic world.
 */
export interface EventIdentity {
  accent: string;
  codename: string;
  /** Short phrase for the grid; the full description comes from the API. */
  kicker: string;
  /**
   * Which track an event belongs to in the first-year view.
   *
   * Presentation only. The backend has no opinion on "tech" versus "non-tech" —
   * it is how the programme is announced to students, not a rule it enforces.
   * The second-year view does not use this at all: there, the split is COMMON
   * versus EXCLUSIVE, which IS derivable from the API's `eligibleYears`.
   */
  discipline: 'NON_TECH' | 'TECH';
  /**
   * Whether this event's participants need to carry an identifier on the day.
   *
   * Presentation metadata, not a rule: it only decides whether the confirmation
   * screen puts the participant id front and centre with a copy control. The id
   * itself always comes from the backend.
   */
  eventDayIdRequired?: boolean;
  /**
   * Sort order within a track.
   *
   * Only the relative order matters. Display order from the API orders the full
   * catalogue; this orders events inside a track, which is a presentation
   * decision the API cannot make because tracks do not exist server-side.
   */
  rank: number;
}

const DEFAULT_IDENTITY: EventIdentity = {
  accent: '#43C9D6',
  codename: 'SECTOR',
  kicker: 'Event',
  discipline: 'TECH',
  rank: 99,
};

const IDENTITIES: Record<string, EventIdentity> = {
  chess: {
    accent: '#C9CEDA',
    codename: 'THE BOARD',
    kicker: 'Classical, over the board',
    discipline: 'NON_TECH',
    rank: 1,
  },
  'tech-debate': {
    accent: '#F0B429',
    codename: 'THE ARENA',
    kicker: 'Ten voices, one motion',
    discipline: 'NON_TECH',
    rank: 2,
  },
  ideathon: {
    accent: '#9D7BEA',
    codename: 'THE LATTICE',
    kicker: 'Problem to pitch',
    discipline: 'TECH',
    rank: 3,
  },
  buildx: {
    accent: '#43C9D6',
    codename: 'THE YARD',
    kicker: 'Receive a theme. Build anything.',
    discipline: 'TECH',
    rank: 4,
  },
  debugging: {
    accent: '#45C98A',
    codename: 'THE STACK',
    kicker: 'Find the fault. Fix it fast.',
    discipline: 'TECH',
    rank: 5,
    // Debugging is run against a live judge on the day; the participant is
    // identified by this id rather than by name at the terminal.
    eventDayIdRequired: true,
  },
  'fix-it': {
    accent: '#E0533D',
    codename: 'THE EXCHANGE',
    kicker: 'Turn the business around',
    discipline: 'TECH',
    rank: 6,
  },
  'rapid-research': {
    accent: '#5B9DE8',
    codename: 'THE ARCHIVE',
    kicker: 'Research under the clock',
    discipline: 'TECH',
    rank: 7,
  },
};

export function identityFor(eventId: EventId): EventIdentity {
  return IDENTITIES[eventId] ?? DEFAULT_IDENTITY;
}
