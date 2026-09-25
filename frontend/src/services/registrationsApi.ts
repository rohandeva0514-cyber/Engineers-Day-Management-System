/**
 * Registration endpoints.
 *
 * Creating a team, adding its members and registering them is a single atomic POST
 * on this backend — there is no invite/accept flow and nothing to orchestrate here.
 */

import type {
  ParticipantRegistrations,
  RegistrationReceipt,
  RegistrationRequest,
} from '@/domain/types';
import { apiRequest } from './apiClient';

/**
 * `POST /api/registrations`
 *
 * Carries an idempotency key. If the response is lost in transit and the same key
 * is replayed, the backend returns the original registration instead of creating a
 * second one — which matters on campus wifi, where a request can succeed and the
 * response never arrive.
 */
export function submitRegistration(
  request: RegistrationRequest,
  signal?: AbortSignal,
): Promise<RegistrationReceipt> {
  return apiRequest<RegistrationReceipt>('/registrations', {
    method: 'POST',
    body: request,
    ...(signal ? { signal } : {}),
  });
}

/** `GET /api/registrations/{participantId}` */
export function fetchRegistrationsByParticipantId(
  participantId: number,
  signal?: AbortSignal,
): Promise<ParticipantRegistrations> {
  return apiRequest<ParticipantRegistrations>(
    `/registrations/${participantId}`,
    signal ? { signal } : {},
  );
}

/**
 * `GET /api/registrations?email=`
 *
 * By email, not roll number: roll numbers repeat, so they cannot address one
 * student. Email is the identity the backend keys on.
 */
export function fetchRegistrationsByEmail(
  email: string,
  signal?: AbortSignal,
): Promise<ParticipantRegistrations> {
  const search = new URLSearchParams({ email: email.trim() });
  return apiRequest<ParticipantRegistrations>(
    `/registrations?${search.toString()}`,
    signal ? { signal } : {},
  );
}
