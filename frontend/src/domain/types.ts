/**
 * Domain model for Engineers' Day 2026.
 *
 * Pure TypeScript. No React, no DOM, no fetch — this module is the one place a
 * reviewer can read to learn the shape of the product, and it must stay importable
 * by the future cinematic layer without dragging the platform UI along with it.
 *
 * Every type here mirrors the Spring Boot wire format exactly (verified against the
 * live API, not inferred). Where the frontend needs a derived concept the backend
 * does not send — `EventAvailability`, for instance — it is computed in
 * `rules.ts` and clearly marked as presentation-only.
 *
 * The backend remains authoritative for every decision. Nothing here is a
 * substitute for a server check; it exists to shape the interface so a student is
 * guided towards a request that will succeed.
 */

/** Slug identifier, e.g. `buildx`, `tech-debate`. Also the API path segment. */
export type EventId = string;

/** Only years 1 and 2 participate in Engineers' Day 2026. */
export type YearLevel = 1 | 2;

export const YEAR_LEVELS: readonly YearLevel[] = [1, 2] as const;

/**
 * How an event is entered.
 *
 * A SOLO event is stored server-side as a team of exactly one, so `minTeamSize`
 * and `maxTeamSize` are both 1. The distinction still matters to the UI: a solo
 * entry shows one participant form and no team name field.
 */
export type ParticipationType = 'SOLO' | 'TEAM';

/**
 * Which registration slot an event consumes.
 *
 * PRIMARY uses up the student's single main-event slot. OPEN can be held
 * alongside it. Sent per event, so nothing here names FIX IT — opening a second
 * event later is a backend data change and this type already covers it.
 */
export type RegistrationSlot = 'PRIMARY' | 'OPEN';

/**
 * What a student may still register for, as decided by the server.
 *
 * A report of a decision already made, never an input to one: the backend
 * re-validates every registration regardless, so a client that ignores this
 * gains nothing. It exists purely so the UI does not have to re-derive the rule
 * and risk disagreeing with the server about it.
 */
export interface RegistrationState {
  hasPrimaryEvent: boolean;
  primaryEventId: EventId | null;
  primaryEventName: string | null;
  hasOpenEvent: boolean;
  openEventId: EventId | null;
  openEventName: string | null;
  canRegisterPrimaryEvent: boolean;
  canRegisterOpenEvent: boolean;
}

/** What a single seat of capacity counts. Sent by the backend per event. */
export type CapacityUnit = 'PARTICIPANT' | 'TEAM';

/**
 * Registration lifecycle.
 *
 * The backend sends the *effective* status: an open event with no seats left
 * already reports `SOLD_OUT`. The frontend never derives this itself.
 */
export type RegistrationStatus = 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'SOLD_OUT';

/** `GET /api/events` and `GET /api/events/{eventId}`. */
export interface Event {
  eventId: EventId;
  name: string;
  description: string;
  eligibleYears: YearLevel[];
  participationType: ParticipationType;
  minTeamSize: number;
  maxTeamSize: number;
  /** `null` when the event has no automatic limit and is closed manually. */
  capacity: number | null;
  capacityUnit: CapacityUnit;
  /** `null` whenever `capacity` is null. */
  seatsTaken: number | null;
  /** `null` whenever `capacity` is null. */
  seatsRemaining: number | null;
  registrationStatus: RegistrationStatus;
  registrationOpen: boolean;
  /** PRIMARY consumes the student's one main-event slot; OPEN does not. */
  registrationSlot: RegistrationSlot;
  /** True when registering issues an event-day access code. */
  requiresAccessCode: boolean;
}

/** A person, as returned by the API. Created implicitly on first registration. */
export interface Participant {
  participantId: number;
  rollNo: string;
  fullName: string;
  email: string;
  yearLevel: YearLevel;
  /** Null only for participants created before these were collected (see V3). */
  phone: string | null;
  branch: string | null;
  division: string | null;
}

/** Present on team registrations, `null` on solo ones. */
export interface Team {
  teamId: number;
  name: string;
  captainRollNo: string;
  size: number;
}

/** One entry of a successful registration response. */
export interface RegistrationEntry {
  registrationId: number;
  participant: Participant;
  /**
   * Event-day access code, or null for events that issue none.
   *
   * Server-generated and persisted. Never derived, defaulted or regenerated on
   * the client — the value a student saves is the value the backend stored.
   */
  accessCode: string | null;
}

/** `POST /api/registrations` success body. */
export interface RegistrationReceipt {
  eventId: EventId;
  eventName: string;
  participationType: ParticipationType;
  team: Team | null;
  registrations: RegistrationEntry[];
  seatsRemaining: number | null;
  registeredAt: string;
  /** What the first participant on the roster may still register for. */
  registrationState: RegistrationState;
}

/** One row of `GET /api/registrations/{participantId}`. */
export interface RegistrationSummary {
  registrationId: number;
  eventId: EventId;
  eventName: string;
  participationType: ParticipationType;
  teamId: number | null;
  teamName: string | null;
  registeredAt: string;
}

/** `GET /api/registrations/{participantId}` and `?rollNo=` body. */
export interface ParticipantRegistrations {
  participant: Participant;
  registrations: RegistrationSummary[];
  registrationState: RegistrationState;
}

/* ---------------------------------------------------------------- requests */

/** One roster entry, exactly as `POST /api/registrations` expects it. */
export interface ParticipantDraft {
  rollNo: string;
  /** Printed on the certificate exactly as entered. */
  fullName: string;
  email: string;
  /** Sent as a number. `null` only while the form is incomplete. */
  yearLevel: YearLevel | null;
  phone: string;
  branch: string;
  division: string;
}

/**
 * A registration request.
 *
 * `participants[0]` is the captain. `teamName` must be present for TEAM events and
 * absent for SOLO ones — the backend rejects the wrong shape either way.
 */
export interface RegistrationRequest {
  eventId: EventId;
  teamName?: string;
  participants: ParticipantDraft[];
  idempotencyKey: string;
}

/* ------------------------------------------------- presentation-only types */

/**
 * The three states the events UI distinguishes.
 *
 * Derived from `registrationStatus` alone — never recomputed from seat counts,
 * because the backend already folds capacity into the status it sends and a second
 * opinion here could only ever disagree with it.
 */
export type EventAvailability = 'OPEN' | 'CLOSED' | 'SOLD_OUT';

/**
 * `GET /api/verify?code=` — what a marshal sees at event-day check-in.
 *
 * Six fields, deliberately. No email, no phone, no roll number, no internal id:
 * the endpoint is public, so anything here is effectively public to anyone
 * holding a code, and none of those are needed to check someone in.
 */
export interface AccessCodeVerification {
  fullName: string;
  branch: string | null;
  division: string | null;
  yearLevel: YearLevel;
  eventId: EventId;
  eventName: string;
  registrationStatus: string;
}
