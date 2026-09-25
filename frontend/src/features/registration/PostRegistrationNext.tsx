import { useState } from 'react';
import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { useEvents } from '@/hooks/useEvents';
import type { RegistrationReceipt } from '@/domain/types';

/**
 * What the student can still do, immediately after registering.
 *
 * The rule is one main event plus FIX IT, and this is the moment it either
 * becomes obvious or stays a mystery. Three outcomes, and only ever one shown:
 *
 *   main event taken, FIX IT free  -> offer FIX IT
 *   FIX IT taken, main event free  -> offer the event list
 *   both taken                     -> say so, and offer nothing
 *
 * The state is read from `receipt.registrationState`, which the server computed
 * from the rows it had just written. Nothing here re-derives the rule, so this
 * panel cannot contradict what the backend would actually accept — and there is
 * no path where it offers something that would then be refused.
 *
 * Restrained on purpose: a bordered panel and a link. No modal, no confetti.
 */
export function PostRegistrationNext({ receipt }: { receipt: RegistrationReceipt }) {
  const state = receipt.registrationState;

  // Which slot did this registration just fill? Answered by comparing ids the
  // server sent, so no event is named here.
  const justRegisteredOpen = receipt.eventId === state.openEventId;

  if (state.hasPrimaryEvent && state.hasOpenEvent) {
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
          <div className="flex items-baseline gap-3">
            <dt className="label-tech w-24 shrink-0">Main event</dt>
            <dd className="text-[15px] text-ink">{state.primaryEventName}</dd>
          </div>
          <div className="flex items-baseline gap-3">
            <dt className="label-tech w-24 shrink-0">Additional</dt>
            <dd className="text-[15px] text-ink">{state.openEventName}</dd>
          </div>
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

  if (justRegisteredOpen && state.canRegisterPrimaryEvent) {
    return (
      <section
        aria-labelledby="next-heading"
        className="clip-notch-tr mt-8 border border-signal/40 bg-signal-soft/30 px-5 py-6 sm:px-6"
      >
        <TechLabel className="mb-2 label-tech-bright">One slot still open</TechLabel>
        <h2 id="next-heading" className="font-display text-xl text-ink sm:text-2xl">
          You can still register for one event
        </h2>
        <p className="mt-3 max-w-prose text-sm leading-relaxed text-muted">
          {state.openEventName} does not use your main-event slot. You can still choose
          one event from the list.
        </p>
        <div className="mt-5">
          <ButtonLink to="/events">View events</ButtonLink>
        </div>
      </section>
    );
  }

  if (state.canRegisterOpenEvent) {
    return <OpenEventInvitation />;
  }

  return null;
}

/**
 * The FIX IT invitation.
 *
 * Shown after a student takes their primary event, when the open slot is still
 * free. Phrased as what it is — a second event that does not compete with the one
 * they just chose — and it names FIX IT rather than saying "one more event",
 * because a student cannot in fact register for any second event.
 *
 * The target is resolved from the event catalogue by SLOT, not from the student's
 * own registrations. That matters: `registrationState.openEventId` is only
 * populated once someone already holds the open event, so reading it here meant
 * this never rendered for the case it exists for. Looking it up by
 * `registrationSlot === 'OPEN'` also keeps the component free of a hardcoded
 * 'fix-it' slug, so a future change to which event is open needs no edit here.
 *
 * Renders nothing if the catalogue is unavailable. A student seeing no panel is
 * better than one sent to a link that may not resolve.
 */
function OpenEventInvitation() {
  const events = useEvents();

  if (events.status !== 'success') return null;

  const openEvent = events.data.find((event) => event.registrationSlot === 'OPEN');
  if (openEvent === undefined) return null;

  return (
    <section
      aria-labelledby="next-heading"
      className="clip-notch-tr mt-8 border border-signal/40 bg-signal-soft/30 px-5 py-6 sm:px-6"
    >
      <TechLabel className="mb-2 label-tech-bright">Special open event</TechLabel>
      <h2 id="next-heading" className="font-display text-xl text-ink sm:text-2xl">
        You can still register for {openEvent.name}
      </h2>
      <p className="mt-3 max-w-prose text-sm leading-relaxed text-muted">
        {openEvent.name} is available as an additional event alongside your primary
        event.
      </p>
      <div className="mt-5 flex flex-wrap gap-3">
        <ButtonLink to={`/events/${openEvent.eventId}`}>
          Register for {openEvent.name} &rarr;
        </ButtonLink>
        <ButtonLink to="/my-registrations" variant="ghost">
          View my registration
        </ButtonLink>
      </div>
    </section>
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
