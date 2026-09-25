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
    'Seven events across both years — from over-the-board chess to a timed debugging sprint. Check what you are eligible for, assemble a team where one is needed, and claim your place.',
  registrationDeadlineLabel: '29 SEP 2026',
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
}

const DEFAULT_IDENTITY: EventIdentity = {
  accent: '#43C9D6',
  codename: 'SECTOR',
  kicker: 'Event',
};

const IDENTITIES: Record<string, EventIdentity> = {
  chess: { accent: '#C9CEDA', codename: 'THE BOARD', kicker: 'Classical, over the board' },
  'tech-debate': { accent: '#F0B429', codename: 'THE ARENA', kicker: 'Ten voices, one motion' },
  'fix-it': { accent: '#E0533D', codename: 'THE EXCHANGE', kicker: 'Turn the business around' },
  ideathon: { accent: '#9D7BEA', codename: 'THE LATTICE', kicker: 'Problem to pitch' },
  buildx: { accent: '#43C9D6', codename: 'THE YARD', kicker: 'Receive a theme. Build anything.' },
  debugging: { accent: '#45C98A', codename: 'THE STACK', kicker: 'Find the fault. Fix it fast.' },
  'rapid-research': {
    accent: '#5B9DE8',
    codename: 'THE ARCHIVE',
    kicker: 'Research under the clock',
  },
};

export function identityFor(eventId: EventId): EventIdentity {
  return IDENTITIES[eventId] ?? DEFAULT_IDENTITY;
}
