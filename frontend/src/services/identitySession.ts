/**
 * Participant identity — the seam where authentication will eventually plug in.
 *
 * The backend has no authentication yet: no login, no token, no session. A
 * participant record is created as a side effect of registering, and
 * `GET /api/registrations/...` is unauthenticated. This module exists so that the
 * rest of the application never learns that.
 *
 * Nothing here is security. `rememberedIdentity` is a convenience so a student who
 * just registered on this device does not have to retype their roll number — it is
 * local to the browser, it proves nothing, and it is never sent as a credential.
 *
 * WHEN EMAIL OTP OR SSO ARRIVES: implement `IdentitySession` against it, swap the
 * instance exported at the bottom, and no page, form or component changes. The
 * pieces that will need real backing are marked below.
 */

import type { Participant, ParticipantRegistrations } from '@/domain/types';
import {
  fetchRegistrationsByParticipantId,
  fetchRegistrationsByRollNo,
} from './registrationsApi';

/** The minimum we keep about "who is using this browser". Not a credential. */
export interface RememberedIdentity {
  participantId: number;
  rollNo: string;
  fullName: string;
}

export interface IdentitySession {
  /** Who this device last registered as, if anyone. */
  current(): RememberedIdentity | null;

  /** Record a successful registration so `/my-registrations` can self-populate. */
  remember(participant: Participant): void;

  /** Forget this device's identity. Purely local. */
  clear(): void;

  /** True once a real verification mechanism backs this session. */
  readonly isVerified: boolean;

  /** Load the remembered participant's registrations. */
  loadOwnRegistrations(signal?: AbortSignal): Promise<ParticipantRegistrations | null>;

  /**
   * Look a participant up by roll number.
   *
   * TEMPORARY. This endpoint is unauthenticated, so this is a lookup, not an
   * authenticated read. It exists because a student who registered on another
   * device has no other way back to their own record. Replace with an OTP
   * challenge before launch.
   */
  lookupByRollNo(rollNo: string, signal?: AbortSignal): Promise<ParticipantRegistrations>;
}

const STORAGE_KEY = 'mtk.identity.v1';

/**
 * Device-local implementation.
 *
 * Every storage access is wrapped: private windows, blocked site data and
 * disabled storage all throw, and none of them should break the page.
 */
class DeviceLocalIdentitySession implements IdentitySession {
  readonly isVerified = false;

  current(): RememberedIdentity | null {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (raw === null) return null;

      const parsed = JSON.parse(raw) as Partial<RememberedIdentity>;
      if (typeof parsed.participantId !== 'number' || typeof parsed.rollNo !== 'string') {
        return null;
      }
      return {
        participantId: parsed.participantId,
        rollNo: parsed.rollNo,
        fullName: typeof parsed.fullName === 'string' ? parsed.fullName : '',
      };
    } catch {
      return null;
    }
  }

  remember(participant: Participant): void {
    try {
      const identity: RememberedIdentity = {
        participantId: participant.participantId,
        rollNo: participant.rollNo,
        fullName: participant.fullName,
      };
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(identity));
    } catch {
      // Storage unavailable. The student can still look themselves up by roll number.
    }
  }

  clear(): void {
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to do.
    }
  }

  async loadOwnRegistrations(signal?: AbortSignal): Promise<ParticipantRegistrations | null> {
    const identity = this.current();
    if (identity === null) return null;
    return fetchRegistrationsByParticipantId(identity.participantId, signal);
  }

  lookupByRollNo(rollNo: string, signal?: AbortSignal): Promise<ParticipantRegistrations> {
    return fetchRegistrationsByRollNo(rollNo, signal);
  }
}

/** Swap this one line when real authentication lands. */
export const identitySession: IdentitySession = new DeviceLocalIdentitySession();
