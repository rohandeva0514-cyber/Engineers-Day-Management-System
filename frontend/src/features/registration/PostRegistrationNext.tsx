import { useState } from 'react';
import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { useEvents } from '@/hooks/useEvents';
import { eventsInSlot, freeSlots } from '@/domain/rules';
import type { Event, RegistrationReceipt, SlotState } from '@/domain/types';

/**
 * What the student can still do, immediately after registering.
 *
 * A student takes three events, one from each registration group, and this is
 * the moment the remaining two either become obvious or stay a mystery. Two
 * outcomes:
 *
 *   every group used   -> confirm all three, and offer nothing further
 *   groups still free  -> name them, and show the events in each
 *
 * The state is read from `receipt.registrationState`, which the server computed
 * from the rows it had just written. Nothing here re-derives the rule, so this
 * panel cannot contradict what the backend would actually accept — and there is
 * no path where it offers something that would then be refused.
 *
 * Restrained on purpose: a bordered panel and a list of links. No modal, no
 * confetti.
 */
export function PostRegistrationNext({ receipt }: { receipt: RegistrationReceipt }) {
  const free = freeSlots(receipt.registrationState);

  if (free.length === 0) {
    return <AllSlotsFilled receipt={receipt} />;
  }

  return <RemainingSlots free={free} />;
}

/** Every group used. The three events are listed back as a receipt. */
function AllSlotsFilled({ receipt }: { receipt: RegistrationReceipt }) {
  return (
    <section
      aria-labelledby="next-heading"
      className="clip-notch-tr mt-8 border border-ok/40 bg-ok-soft/30 px-5 py-6 sm:px-6"
    >
      <TechLabel className="mb-2 text-ok">Registration complete</TechLabel>
      <h2 id="next-heading" className="font-display text-xl text-ink sm:text-2xl">
        You&rsquo;re all set
      </h2>

      <dl className="mt-4 space-y-2">
        {receipt.registrationState.slots.map((slot) => (
          <div key={slot.slot} className="flex items-baseline gap-3">
            <dt className="label-tech w-24 shrink-0">{slot.label}</dt>
            <dd className="text-[15px] text-ink">{slot.eventName}</dd>
          </div>
        ))}
      </dl>

      <p className="mt-4 max-w-prose text-sm leading-relaxed text-muted">
        You have reached the maximum number of registrations. There is nothing further
        to enter.
      </p>

      <div className="mt-5">
        <ButtonLink to="/my-registrations">View my registrations</ButtonLink>
      </div>
    </section>
  );
}

/**
 * The groups still open, each with the events it contains.
 *
 * Showing the actual events rather than "one slot remaining" is the point: the
 * choice is between named events, and a student who has to go back to the
 * catalogue and work out which ones compete has been told nothing useful.
 *
 * The events come from the catalogue grouped by `registrationSlot`, so nothing
 * here names BuildX, Ideathon or any other event. If the catalogue is
 * unavailable the panel still renders with the group names and a link to the
 * events page — degraded, but never empty and never wrong.
 */
function RemainingSlots({ free }: { free: SlotState[] }) {
  const events = useEvents();
  const catalogue = events.status === 'success' ? events.data : [];

  return (
    <section
      aria-labelledby="next-heading"
      className="clip-notch-tr mt-8 border border-signal/40 bg-signal-soft/30 px-5 py-6 sm:px-6"
    >
      <TechLabel className="mb-2 label-tech-bright">
        {free.length === 1 ? 'One slot still open' : `${free.length} slots still open`}
      </TechLabel>
      <h2 id="next-heading" className="font-display text-xl text-ink sm:text-2xl">
        {free.length === 1
          ? 'You can still register for one more event'
          : `You can still register for ${free.length} more events`}
      </h2>
      <p className="mt-3 max-w-prose text-sm leading-relaxed text-muted">
        Each student takes one event from every group. These are still yours to claim &mdash;
        one event from each.
      </p>

      <ul className="mt-5 space-y-4">
        {free.map((slot) => (
          <li key={slot.slot}>
            <TechLabel className="mb-1.5">{slot.label}</TechLabel>
            <SlotChoices options={eventsInSlot(catalogue, slot.slot)} />
          </li>
        ))}
      </ul>

      <div className="mt-6 flex flex-wrap gap-3">
        <ButtonLink to="/events">View all events</ButtonLink>
        <ButtonLink to="/my-registrations" variant="ghost">
          View my registrations
        </ButtonLink>
      </div>
    </section>
  );
}

/**
 * The events in one open group, as direct links.
 *
 * Events the organisers have closed are still listed, unlinked: a student
 * comparing their options should see that the group has four events and that one
 * of them is shut, rather than a short list they cannot account for.
 */
function SlotChoices({ options }: { options: Event[] }) {
  if (options.length === 0) return null;

  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-2">
      {options.map((event) => (
        <li key={event.eventId} className="text-[15px]">
          {event.registrationOpen ? (
            <ButtonLink to={`/events/${event.eventId}`} variant="ghost">
              {event.name}
            </ButtonLink>
          ) : (
            <span className="clip-notch-sm inline-flex h-10 items-center border border-line px-5 font-mono text-[13px] uppercase tracking-[0.14em] text-faint line-through">
              {event.name}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
}

/**
 * The event-day access code.
 *
 * Shown only when the backend actually issued one — the component checks for the
 * code on the receipt rather than deciding which events deserve it. Nothing here
 * names Debugging, and nothing here generates anything: the value is whatever the
 * server persisted, and a student who reloads, or comes back next week, sees the
 * same eight characters.
 *
 * Given the most visual weight on the screen. For the events that use it, this is
 * the one thing on the page that matters after the student closes the tab.
 */
export function EventDayIdentifier({ receipt }: { receipt: RegistrationReceipt }) {
  const [copied, setCopied] = useState(false);

  const entry = receipt.registrations[0];
  const code = entry?.accessCode ?? null;

  // No code means the event does not issue one. Not an error, not a fallback to
  // some other identifier — just nothing to show.
  if (code === null) return null;

  const copy = () => {
    navigator.clipboard
      ?.writeText(code)
      .then(() => {
        setCopied(true);
        window.setTimeout(() => setCopied(false), 2000);
      })
      .catch(() => setCopied(false));
  };

  return (
    <section
      aria-labelledby="eventday-heading"
      className="clip-notch-tr mt-8 border border-orange/50 bg-orange-soft/30 px-5 py-6 sm:px-6"
    >
      <TechLabel className="mb-2 text-orange">Event-day access</TechLabel>
      <h2 id="eventday-heading" className="font-display text-xl text-ink sm:text-2xl">
        Your {receipt.eventName} access ID
      </h2>

      {/* Wide tracking and a large size: this gets read off a phone and typed at
          a terminal, sometimes by someone else. */}
      <p
        className="mt-5 font-mono text-4xl tracking-[0.18em] text-ink sm:text-5xl"
        data-tabular
      >
        {code}
      </p>

      <div className="mt-6 border-l-2 border-orange pl-4">
        <TechLabel className="mb-1.5 text-orange">Important</TechLabel>
        <p className="max-w-prose text-sm leading-relaxed text-muted">
          <strong className="text-ink">Save this ID somewhere safe.</strong> You will
          need it on the day of {receipt.eventName} to start the event. It is also
          listed under My Registrations.
        </p>
      </div>

      <div className="mt-5">
        <button
          type="button"
          onClick={copy}
          className="clip-notch-sm border border-line-bright px-4 py-2 font-mono text-xs uppercase tracking-[0.2em] text-ink transition-colors hover:border-signal hover:text-signal"
        >
          {copied ? 'Copied' : 'Copy ID'}
        </button>
        {/* Announced as well as shown: the confirmation is not colour alone. */}
        <span aria-live="polite" className="sr-only">
          {copied ? `Access ID ${code} copied` : ''}
        </span>
      </div>
    </section>
  );
}
