import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { useRegistrationState } from '@/hooks/useRegistrationState';
import type { Event } from '@/domain/types';

/**
 * The register action, reflecting what this student can actually do.
 *
 * Four outcomes, decided from the server's own report of the student's slots:
 *
 *   already in this event          -> say so, link to their registrations
 *   this event needs the primary
 *     slot and it is taken         -> explain, and offer the open event instead
 *   otherwise                      -> register
 *
 * NOT authoritative, and written on the assumption that it will sometimes be
 * wrong. The state is fetched once on mount; a student registering on a second
 * device, or in another tab, can get past any refusal here. That is fine — the
 * backend re-checks every submission and returns PRIMARY_EVENT_ALREADY_TAKEN,
 * which the registration form renders. This exists to save a wasted form fill,
 * not to enforce anything.
 *
 * When the device is unrecognised the state is null and the plain register
 * action is shown. Unknown must never mean blocked: a student on a fresh phone
 * has to be able to register.
 */
export function SlotAwareAction({ event }: { event: Event }) {
  const state = useRegistrationState();

  const alreadyInThisEvent =
    state !== null &&
    (state.primaryEventId === event.eventId || state.openEventId === event.eventId);

  if (alreadyInThisEvent) {
    return (
      <div className="border border-ok/40 bg-ok-soft/30 p-6">
        <TechLabel className="mb-2 text-ok">Already registered</TechLabel>
        <h2 className="text-lg text-ink">You are registered for {event.name}</h2>
        <p className="mt-2 max-w-xl text-sm text-muted">
          There is nothing further to do for this event.
        </p>
        <div className="mt-5 flex flex-wrap gap-3">
          <ButtonLink to="/my-registrations">View registration</ButtonLink>
          <ButtonLink to="/events" variant="ghost">
            Back to events
          </ButtonLink>
        </div>
      </div>
    );
  }

  // Only a PRIMARY event can be blocked by a full primary slot. The open event
  // is never blocked this way — which is the whole rule, expressed once.
  const blockedByPrimarySlot =
    state !== null && event.registrationSlot === 'PRIMARY' && state.hasPrimaryEvent;

  if (blockedByPrimarySlot) {
    return (
      <div className="border border-line bg-panel p-6">
        <TechLabel className="mb-2">Registration restricted</TechLabel>
        <h2 className="text-lg text-ink">You already have a main event</h2>
        <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted">
          You are registered for{' '}
          <strong className="text-ink">{state.primaryEventName}</strong>. Each student
          takes one main event, so {event.name} is not available to you.
        </p>

        <div className="mt-5 flex flex-wrap gap-3">
          {state.canRegisterOpenEvent && state.openEventId !== null ? (
            <ButtonLink to={`/events/${state.openEventId}`}>Register for FIX IT</ButtonLink>
          ) : state.canRegisterOpenEvent ? (
            <ButtonLink to="/events">See what is still open</ButtonLink>
          ) : null}
          <ButtonLink to="/events" variant="ghost">
            Back to events
          </ButtonLink>
        </div>
      </div>
    );
  }

  const isOpenSlot = event.registrationSlot === 'OPEN';

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      <ButtonLink to={`/register/${event.eventId}`} size="lg">
        Register for {event.name}
      </ButtonLink>
      <p className="text-xs leading-relaxed text-faint sm:max-w-xs">
        {isOpenSlot
          ? 'This event does not use your main-event slot — you can enter it alongside one other event.'
          : 'Eligibility, team size and capacity are verified by the server when you submit.'}
      </p>
    </div>
  );
}
