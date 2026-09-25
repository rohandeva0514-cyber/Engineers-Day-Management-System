import { Link } from 'react-router-dom';
import type { Event } from '@/domain/types';
import {
  availabilityOf,
  eligibilityLabel,
  hasPublishedCapacity,
  participationLabel,
  teamSizeLabel,
} from '@/domain/rules';
import { StatusBadge } from '@/components/StatusBadge';
import { TechLabel } from '@/components/Panel';
import { identityFor } from '@/data/siteContent';
import { pad2 } from '@/lib/format';

/**
 * One event in the discovery grid.
 *
 * Rendered from the API `Event` for every event — there is no per-event JSX
 * anywhere in this application. Adding an eighth event is a backend migration and
 * one entry in the identity map; no component changes.
 *
 * Not a card in the SaaS sense: square corners, hairline border, no shadow, no
 * lift. The accent rail on the left is the only colour it carries, and it is the
 * event's own.
 */
export function EventCard({ event, index }: { event: Event; index: number }) {
  const identity = identityFor(event.eventId);
  const availability = availabilityOf(event);
  const isOpen = availability === 'OPEN';

  return (
    <article className="group relative flex h-full flex-col border border-line bg-panel transition-colors duration-200 hover:border-line-bright focus-within:border-signal">
      {/* Event accent rail. Grows on hover — the one piece of motion on the card. */}
      <span
        aria-hidden="true"
        className="absolute left-0 top-0 h-10 w-[3px] transition-all duration-200 group-hover:h-full"
        style={{ backgroundColor: identity.accent }}
      />

      <div className="flex items-start justify-between gap-3 border-b border-line px-5 py-3.5 pl-6">
        <div className="min-w-0">
          <TechLabel className="truncate">
            <span data-tabular>{pad2(index + 1)}</span>
            <span className="mx-2 text-line-bright">/</span>
            {identity.codename}
          </TechLabel>
        </div>
        <StatusBadge status={availability} className="shrink-0" />
      </div>

      <div className="flex flex-1 flex-col px-5 py-5 pl-6">
        <h3 className="font-display text-xl leading-tight text-ink sm:text-2xl">
          {/* Stretched link: the whole card is the hit area, but only one
              focusable element exists, so keyboard order stays sane. */}
          <Link
            to={`/events/${event.eventId}`}
            className="after:absolute after:inset-0 after:content-[''] hover:text-signal focus:outline-none"
          >
            {event.name}
          </Link>
        </h3>

        <p className="mt-2.5 line-clamp-3 flex-1 text-sm leading-relaxed text-muted">
          {event.description}
        </p>

        <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-line pt-4">
          <Meta label="Eligibility" value={eligibilityLabel(event)} />
          <Meta label="Entry" value={participationLabel(event)} />
          <Meta label="Team size" value={teamSizeLabel(event)} />
          <Meta
            label="Seats"
            value={
              hasPublishedCapacity(event)
                ? `${event.seatsRemaining} of ${event.capacity} left`
                : 'No published limit'
            }
            tabular={hasPublishedCapacity(event)}
          />
        </dl>

        <p
          className={
            'mt-5 font-mono text-[11px] uppercase tracking-[0.14em] ' +
            (isOpen ? 'text-signal' : 'text-faint')
          }
        >
          {isOpen ? 'View & register →' : 'View details →'}
        </p>
      </div>
    </article>
  );
}

function Meta({ label, value, tabular = false }: { label: string; value: string; tabular?: boolean }) {
  return (
    <div className="min-w-0">
      <dt className="label-tech">{label}</dt>
      <dd className="mt-1 truncate text-[13px] text-ink" {...(tabular ? { 'data-tabular': true } : {})}>
        {value}
      </dd>
    </div>
  );
}
