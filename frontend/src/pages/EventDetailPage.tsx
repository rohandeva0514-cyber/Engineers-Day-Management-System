import { Link, useParams } from 'react-router-dom';
import { useEvent } from '@/hooks/useEvents';
import { ErrorState, PageMessage } from '@/components/ErrorState';
import { Spinner } from '@/components/Spinner';
import { Button, ButtonLink } from '@/components/Button';
import { SlotAwareAction } from '@/features/registration/SlotAwareAction';
import { DataField, TechLabel } from '@/components/Panel';
import { StatusBadge } from '@/components/StatusBadge';
import { CapacityMeter } from '@/features/events/CapacityMeter';
import { identityFor } from '@/data/siteContent';
import {
  availabilityOf,
  eligibilityLabel,
  isSolo,
  participationLabel,
  requiresExactTeamSize,
  teamSizeLabel,
} from '@/domain/rules';
import type { Event } from '@/domain/types';

/**
 * One event, in full.
 *
 * Every fact on this page is server data. The three availability states are taken
 * straight from `registrationStatus` — the frontend does not recompute SOLD_OUT
 * from seat counts, because the backend already folds capacity into the status and
 * a second opinion could only ever contradict it.
 */
export function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const event = useEvent(eventId);

  if (event.status === 'loading' || event.status === 'idle') {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 sm:px-6">
        <Spinner label="Loading event" />
      </div>
    );
  }

  if (event.status === 'error') {
    const notFound = event.error.code === 'EVENT_NOT_FOUND';
    return (
      <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
        {notFound ? (
          <PageMessage
            label="404 / Not found"
            title="No such event"
            action={<ButtonLink to="/events">Browse all events</ButtonLink>}
          >
            <p>
              There is no event with the id <span className="font-mono text-ink">{eventId}</span>.
            </p>
          </PageMessage>
        ) : (
          <ErrorState
            error={event.error}
            onRetry={event.reload}
            backTo={{ to: '/events', label: 'All events' }}
          />
        )}
      </div>
    );
  }

  return <EventDetail event={event.data} onReload={event.reload} />;
}

function EventDetail({ event, onReload }: { event: Event; onReload: () => void }) {
  const identity = identityFor(event.eventId);
  const availability = availabilityOf(event);
  const isOpen = availability === 'OPEN';

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6 sm:py-14">
      <nav aria-label="Breadcrumb" className="mb-8">
        <Link to="/events" className="label-tech transition-colors hover:text-ink">
          ← All events
        </Link>
      </nav>

      <header className="relative border-b border-line pb-8 pl-5">
        <span
          aria-hidden="true"
          className="absolute left-0 top-1 h-16 w-[3px]"
          style={{ backgroundColor: identity.accent }}
        />

        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <TechLabel bright>{identity.codename}</TechLabel>
          <StatusBadge status={availability} size="md" />
        </div>

        <h1 className="mt-4 font-display text-4xl leading-none tracking-tight text-ink sm:text-6xl">
          {event.name}
        </h1>

        <p className="mt-5 max-w-2xl text-base leading-relaxed text-muted">{event.description}</p>
      </header>

      {/* Entry requirements. A plain definition grid — this is reference material a
          student scans before deciding, not a place for visual invention. */}
      <section aria-labelledby="requirements" className="border-b border-line py-8">
        <h2 id="requirements" className="sr-only">
          Entry requirements
        </h2>

        <dl className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <DataField label="Eligibility">{eligibilityLabel(event)}</DataField>
          <DataField label="Entry type">{participationLabel(event)}</DataField>
          <DataField label="Team size">{teamSizeLabel(event)}</DataField>
          <div>
            <CapacityMeter event={event} />
          </div>
        </dl>
      </section>

      {/* Instructions. The API carries no instructions field in this milestone, so
          what is shown is derived strictly from the entry rules it does send —
          nothing invented, and the section grows naturally when the field lands. */}
      <section aria-labelledby="how-to-enter" className="border-b border-line py-8">
        <TechLabel className="mb-3">Before you register</TechLabel>
        <h2 id="how-to-enter" className="mb-4 text-xl text-ink">
          How to enter
        </h2>

        <ol className="max-w-2xl space-y-3 text-sm leading-relaxed text-muted">
          <li className="flex gap-3">
            <span className="label-tech mt-0.5 shrink-0">01</span>
            <span>
              Confirm you are eligible: this event is open to{' '}
              <span className="text-ink">{eligibilityLabel(event).toLowerCase()}</span> students.
            </span>
          </li>
          <li className="flex gap-3">
            <span className="label-tech mt-0.5 shrink-0">02</span>
            <span>
              {isSolo(event) ? (
                <>
                  Have your <span className="text-ink">roll number, full name and college email</span>{' '}
                  ready. You register as an individual.
                </>
              ) : (
                <>
                  Assemble your team &mdash;{' '}
                  <span className="text-ink">{teamSizeLabel(event).toLowerCase()}</span>. You will need
                  every member&rsquo;s roll number, name, email and year before you submit
                  {requiresExactTeamSize(event) && (
                    <>
                      , and all {event.minTeamSize} must be entered together &mdash; partial rosters
                      are not accepted
                    </>
                  )}
                  .
                </>
              )}
            </span>
          </li>
          <li className="flex gap-3">
            <span className="label-tech mt-0.5 shrink-0">03</span>
            <span>
              Submit the registration. Your place is confirmed only once the server accepts it
              {event.capacity !== null && <> &mdash; seats are allocated in the order they arrive</>}.
            </span>
          </li>
        </ol>
      </section>

      {/* The action. Never a disabled button with no explanation: when registration
          is not available, the reason is stated and a way onward is offered. */}
      <section className="py-8">
        {isOpen ? (
          <SlotAwareAction event={event} />
        ) : (
          <div className="border border-line bg-panel p-6">
            <TechLabel className="mb-2">Registration unavailable</TechLabel>
            <h2 className="text-lg text-ink">
              {availability === 'SOLD_OUT'
                ? `${event.name} is full`
                : `Registration for ${event.name} is closed`}
            </h2>
            <p className="mt-2 max-w-xl text-sm text-muted">
              {availability === 'SOLD_OUT'
                ? 'Every seat for this event has been taken. Check the other events — several still have places.'
                : 'The organisers are no longer accepting registrations for this event.'}
            </p>
            <div className="mt-5 flex flex-wrap gap-3">
              <ButtonLink to="/events" variant="secondary">
                Browse other events
              </ButtonLink>
              <Button variant="ghost" onClick={onReload}>
                Refresh status
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
