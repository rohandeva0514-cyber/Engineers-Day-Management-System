import { useEffect, useState } from 'react';
import type { RegistrationState } from '@/domain/types';
import { identitySession } from '@/services/identitySession';

/**
 * What the student on this device may still register for.
 *
 * Returns null when the device has never registered, or when the lookup fails —
 * both mean "we do not know", and the UI must degrade to offering everything
 * rather than to blocking. A student on a new phone has to be able to register.
 *
 * The state itself is computed by the server and read straight off the API
 * response. Nothing here re-derives the rule, which is the point: there is one
 * implementation of "one main event plus FIX IT", it lives in
 * `RegistrationSlots` on the backend, and this only reports it.
 *
 * Never authoritative. Every refusal shown on the strength of this is confirmed
 * by the server on submit, and a device that has forgotten its identity is
 * simply offered the choice and refused later if it was wrong.
 */
export function useRegistrationState(): RegistrationState | null {
  const [state, setState] = useState<RegistrationState | null>(null);

  useEffect(() => {
    if (identitySession.current() === null) return;

    const controller = new AbortController();

    identitySession
      .loadOwnRegistrations(controller.signal)
      .then((registrations) => {
        if (controller.signal.aborted) return;
        setState(registrations?.registrationState ?? null);
      })
      .catch(() => {
        // Offline, cleared record, deleted participant. "Unknown" is the safe
        // answer: the UI offers everything and the server decides on submit.
        if (!controller.signal.aborted) setState(null);
      });

    return () => controller.abort();
  }, []);

  return state;
}
