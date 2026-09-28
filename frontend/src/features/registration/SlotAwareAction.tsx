import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { useEvents } from '@/hooks/useEvents';
import { useRegistrationState } from '@/hooks/useRegistrationState';
import { eventsInSlot, freeSlots, isRegisteredFor, isSlotBlocked, slotStateFor } from '@/domain/rules';
import type { Event, RegistrationState, SlotState } from '@/domain/types';

/**
 * The register action, reflecting what this student can actually do.
 *
 * Three outcomes, decided from the server's own report of the student's slots:
 *
 *   already in this event        -> say so, link to their registrations
 *   this event's group is spent
 *     on a different event       -> explain, and name the groups still free
 *   otherwise                    -> register, and say what it costs
 *
 * NOT authoritative, and written on the assumption that it will sometimes be
 * wrong. The state is fetched once on mount; a student registering on a second
 * device, or in another tab, can get past any refusal here. That is fine — the
 * backend re-checks every submission and returns EVENT_SLOT_ALREADY_TAKEN,
 * which the registration form renders. This exists to save a wasted form fill,
 * not to enforce anything.
 *
 * When the device is unrecognised the state is null and the plain register
 * action is shown. Unknown must never mean blocked: a student on a fresh phone
 * has to be able to register.
 *
 * Both hooks are called once, here, and the results passed down. Neither is
 * cached, so a child calling `useRegistrationState` for itself would be a second
 * request for an answer this component already holds.
 */
export function SlotAwareAction({ event }: { event: Event }) {
  const state = useRegistrationState();

  if (isRegisteredFor(state, event.eventId)) {
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

  const slot = slotStateFor(state, event.registrationSlot);

  if (slot !== null && isSlotBlocked(state, event)) {
    return <SlotTaken event={event} slot={slot} state={state} />;
  }

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      <ButtonLink to={`/register/${event.eventId}`} size="lg">
        Register for {event.name}
      </ButtonLink>
      <SlotCost event={event} />
    </div>
  );
}

/**
 * The refusal: this group is already spent on another event.
 *
 * Names the event they hold and the groups still open, because "you can only
 * take one Build event" is not actionable on its own — a blocked student needs
 * somewhere to go next. Both come from the server's report, so nothing here
 * names an event or a group.
 */
function SlotTaken({
  event,
  slot,
  state,
}: {
  event: Event;
  slot: SlotState;
  state: RegistrationState | null;
}) {
  const free = freeSlots(state);
  const group = slot.label.toLowerCase();

  return (
    <div className="border border-line bg-panel p-6">
      <TechLabel className="mb-2">Registration restricted</TechLabel>
      <h2 className="text-lg text-ink">Your {group} event is already chosen</h2>
      <p className="mt-2 max-w-xl text-sm leading-relaxed text-muted">
        You are registered for <strong className="text-ink">{slot.eventName}</strong>. Each
        student takes one {group} event, so {event.name} is not available to you.
      </p>

      <p className="mt-3 max-w-xl text-sm leading-relaxed text-faint">
        {free.length === 0
          ? 'You have registered for all three of your events.'
          : `Still open to you: ${free.map((entry) => entry.label).join(', ')}.`}
      </p>

      <div className="mt-5 flex flex-wrap gap-3">
        {free.length > 0 && <ButtonLink to="/events">See what is still open</ButtonLink>}
        <ButtonLink to="/my-registrations" variant="ghost">
          View my registrations
        </ButtonLink>
      </div>
    </div>
  );
}

/**
 * The line under the register button: what choosing this event costs.
 *
 * The other events in the same group become unavailable, and that is the one
 * consequence a student cannot see from this page and cannot undo. The rivals
 * are found by grouping the catalogue on `registrationSlot`, so no event is
 * named here.
 *
 * Falls back to the generic line when the catalogue has not loaded. A student
 * must never be left with a blank where an explanation should be.
 */
function SlotCost({ event }: { event: Event }) {
  const events = useEvents();

  const rivals =
    events.status === 'success'
      ? eventsInSlot(events.data, event.registrationSlot).filter(
          (other) => other.eventId !== event.eventId,
        )
      : [];

  return (
    <p className="text-xs leading-relaxed text-faint sm:max-w-sm">
      {rivals.length === 0
        ? 'Eligibility, team size and capacity are verified by the server when you submit.'
        : `This is your ${event.registrationSlotLabel.toLowerCase()} event — entering it means you cannot also register for ${rivals.map((other) => other.name).join(' or ')}.`}
    </p>
  );
}
