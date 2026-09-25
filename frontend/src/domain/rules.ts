/**
 * Presentation helpers derived from event data.
 *
 * These are NOT business rules. The backend owns eligibility, team size, capacity
 * and duplicate detection, and it re-checks every one of them on submit. Everything
 * here exists to shape the interface — how many member slots to render, whether to
 * enable the submit button, what to call a team size in a sentence — so a student
 * is guided towards a request that will succeed instead of discovering the rules
 * through rejections.
 *
 * Rule of thumb applied throughout: if a check here disagrees with the server, the
 * server is right and the UI shows the server's answer.
 */

import type {
  Event,
  EventAvailability,
  ParticipantDraft,
  RegistrationStatus,
  YearLevel,
} from './types';

/* ------------------------------------------------------------ availability */

const AVAILABILITY: Record<RegistrationStatus, EventAvailability> = {
  REGISTRATION_OPEN: 'OPEN',
  REGISTRATION_CLOSED: 'CLOSED',
  SOLD_OUT: 'SOLD_OUT',
};

export function availabilityOf(event: Event): EventAvailability {
  return AVAILABILITY[event.registrationStatus] ?? 'CLOSED';
}

export const AVAILABILITY_LABEL: Record<EventAvailability, string> = {
  OPEN: 'OPEN',
  CLOSED: 'CLOSED',
  SOLD_OUT: 'SOLD OUT',
};

/* ------------------------------------------------------------------ teams */

export function isSolo(event: Event): boolean {
  return event.participationType === 'SOLO';
}

/** True for a fixed-size team event — Tech Debate's exactly 10. */
export function requiresExactTeamSize(event: Event): boolean {
  return event.participationType === 'TEAM' && event.minTeamSize === event.maxTeamSize;
}

/**
 * How many member rows the form should start with.
 *
 * Fixed-size events open with the full roster already laid out, because a student
 * needs to see up front that Tech Debate wants ten people — discovering it one
 * "add member" click at a time is a worse experience than being shown the shape.
 */
export function initialRosterSize(event: Event): number {
  return requiresExactTeamSize(event) ? event.minTeamSize : Math.max(1, event.minTeamSize);
}

/** Whether the roster can grow. False for solo and for fixed-size events. */
export function canAddMembers(event: Event, currentSize: number): boolean {
  return !isSolo(event) && !requiresExactTeamSize(event) && currentSize < event.maxTeamSize;
}

/** Whether a row can be removed without dropping below the minimum. */
export function canRemoveMembers(event: Event, currentSize: number): boolean {
  return !isSolo(event) && !requiresExactTeamSize(event) && currentSize > event.minTeamSize;
}

export function isRosterSizeValid(event: Event, size: number): boolean {
  return size >= event.minTeamSize && size <= event.maxTeamSize;
}

/** Human phrasing for a team requirement. "exactly 10 members" reads; "10 to 10" does not. */
export function teamSizeLabel(event: Event): string {
  if (isSolo(event)) return 'Solo entry';
  if (requiresExactTeamSize(event)) return `Exactly ${event.minTeamSize} members`;
  if (event.minTeamSize === event.maxTeamSize) return `${event.minTeamSize} members`;
  return `${event.minTeamSize}–${event.maxTeamSize} members`;
}

export function participationLabel(event: Event): string {
  return isSolo(event) ? 'SOLO' : 'TEAM';
}

/* ------------------------------------------------------------- eligibility */

export function isYearEligible(event: Event, year: YearLevel): boolean {
  return event.eligibleYears.includes(year);
}

/** "1st year only", "2nd year only", "1st & 2nd year". */
export function eligibilityLabel(event: Event): string {
  const years = [...event.eligibleYears].sort((a, b) => a - b);
  if (years.length === 0) return 'Not open';
  if (years.length === 1) return `${ordinalYear(years[0]!)} year only`;
  return years.map(ordinalYear).join(' & ') + ' year';
}

export function ordinalYear(year: YearLevel): string {
  return year === 1 ? '1st' : '2nd';
}

/* --------------------------------------------------------------- capacity */

/**
 * Whether this event has a published capacity at all.
 *
 * `capacity: null` is a real and expected state — most events are closed manually
 * rather than by a seat limit, and Ideathon's number is not finalised. The UI must
 * render that as "no published limit", never as zero and never as an invented
 * figure.
 */
export function hasPublishedCapacity(event: Event): boolean {
  return event.capacity !== null && event.seatsRemaining !== null;
}

export function capacityUnitLabel(event: Event, count: number): string {
  const unit = event.capacityUnit === 'TEAM' ? 'team' : 'seat';
  return count === 1 ? unit : `${unit}s`;
}

/** Fraction of capacity consumed, 0–1. Null when there is no published capacity. */
export function capacityFraction(event: Event): number | null {
  if (event.capacity === null || event.seatsTaken === null || event.capacity === 0) return null;
  return Math.min(1, Math.max(0, event.seatsTaken / event.capacity));
}

/** True when few seats remain — used to raise the visual urgency, nothing more. */
export function isScarce(event: Event): boolean {
  const fraction = capacityFraction(event);
  return fraction !== null && fraction >= 0.8 && (event.seatsRemaining ?? 0) > 0;
}

/* ------------------------------------------------------------------ roster */

export function emptyParticipant(): ParticipantDraft {
  return {
    rollNo: '',
    fullName: '',
    email: '',
    yearLevel: null,
    phone: '',
    branch: '',
    division: '',
  };
}

export function isParticipantComplete(draft: ParticipantDraft): boolean {
  return (
    draft.rollNo.trim() !== '' &&
    draft.fullName.trim() !== '' &&
    draft.email.trim() !== '' &&
    draft.yearLevel !== null &&
    draft.phone.trim() !== '' &&
    draft.branch.trim() !== '' &&
    draft.division.trim() !== ''
  );
}

/**
 * Shape-only phone check, mirroring the server's.
 *
 * Deliberately permissive: separators are ignored and 7-15 digits are accepted,
 * which is the full E.164 range. A stricter rule here — ten digits, say — would
 * start refusing real numbers, and the server would accept what the form had
 * already rejected.
 */
export function isPhoneShapeValid(phone: string): boolean {
  const digits = phone.replace(/[^0-9]/g, '');
  return /^[+]?[0-9 ()-]+$/.test(phone.trim()) && digits.length >= 7 && digits.length <= 15;
}

/**
 * Emails duplicated within one roster, lower-cased for comparison.
 *
 * Keyed on email because that is the student identity — two teammates may share
 * a roll number and are two different people, while the same email twice is the
 * same person listed twice.
 *
 * The backend refuses these outright; catching it in the form avoids a
 * round-trip that ends in a rejection the student could have seen immediately.
 */
export function duplicateEmails(roster: ParticipantDraft[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();

  for (const draft of roster) {
    const key = draft.email.trim().toLowerCase();
    if (key === '') continue;
    if (seen.has(key)) duplicates.add(key);
    seen.add(key);
  }
  return [...duplicates];
}
