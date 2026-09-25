import { Link, useParams } from 'react-router-dom';
import { useEvent } from '@/hooks/useEvents';
import { RegistrationForm } from '@/features/registration/RegistrationForm';
import { ErrorState, PageMessage } from '@/components/ErrorState';
import { Spinner } from '@/components/Spinner';
import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { StatusBadge } from '@/components/StatusBadge';
import {
  availabilityOf,
  eligibilityLabel,
  hasPublishedCapacity,
  teamSizeLabel,
} from '@/domain/rules';

/**
 * The registration route.
 *
 * Loads the event fresh rather than trusting anything carried from the events grid:
 * an event that was open when the list rendered may have closed or sold out since,
 * and starting someone on a ten-member roster they cannot submit is a worse outcome
 * than a moment's loading.
 */
export function RegisterPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const event = useEvent(eventId);

  if (event.status === 'loading' || event.status === 'idle') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 sm:px-6">
        <Spinner label="Loading event" />
      </div>
    );
  }

  if (event.status === 'error') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        {event.error.code === 'EVENT_NOT_FOUND' ? (
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

  const data = event.data;
  const availability = availabilityOf(data);

  // Not open: refuse up front rather than letting someone fill in ten members and
  // discover it on submit.
  if (availability !== 'OPEN') {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <nav aria-label="Breadcrumb" className="mb-8">
          <Link to={`/events/${data.eventId}`} className="label-tech hover:text-ink">
            ← {data.name}
          </Link>
        </nav>

        <div className="border border-line bg-panel p-6 sm:p-8">
          <div className="mb-4">
            <StatusBadge status={availability} size="md" />
          </div>
          <h1 className="text-2xl text-ink sm:text-3xl">
            {availability === 'SOLD_OUT'
              ? `${data.name} is full`
              : `Registration for ${data.name} is closed`}
          </h1>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted">
            {availability === 'SOLD_OUT'
              ? 'Every seat has been taken. Nothing was submitted — check the other events, several still have places.'
              : 'The organisers are no longer accepting registrations for this event. Nothing was submitted.'}
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <ButtonLink to="/events">Browse other events</ButtonLink>
            <ButtonLink to={`/events/${data.eventId}`} variant="ghost">
              Event details
            </ButtonLink>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
      <nav aria-label="Breadcrumb" className="mb-8">
        <Link to={`/events/${data.eventId}`} className="label-tech transition-colors hover:text-ink">
          ← {data.name}
        </Link>
      </nav>

      <header className="border-b border-line pb-6">
        <TechLabel bright className="mb-3">
          Registration
        </TechLabel>
        <h1 className="font-display text-3xl leading-tight text-ink sm:text-4xl">{data.name}</h1>

        <dl className="mt-5 flex flex-wrap gap-x-8 gap-y-3">
          <Summary label="Eligibility" value={eligibilityLabel(data)} />
          <Summary label="Team size" value={teamSizeLabel(data)} />
          {hasPublishedCapacity(data) && (
            <Summary
              label="Seats left"
              value={`${data.seatsRemaining} of ${data.capacity}`}
              tabular
            />
          )}
        </dl>
      </header>

      <div className="pt-8">
        <RegistrationForm event={data} />
      </div>
    </div>
  );
}

function Summary({ label, value, tabular = false }: { label: string; value: string; tabular?: boolean }) {
  return (
    <div>
      <dt className="label-tech">{label}</dt>
      <dd className="mt-1 text-sm text-ink" {...(tabular ? { 'data-tabular': true } : {})}>
        {value}
      </dd>
    </div>
  );
}
