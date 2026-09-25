import {
  availabilityOf,
  AVAILABILITY_LABEL,
  eligibilityLabel,
  participationLabel,
  teamSizeLabel,
} from '@/domain/rules';
import { Link } from 'react-router-dom';
import { identityFor } from '@/data/siteContent';
import type { Mission } from '@/domain/missions';

interface MissionCardProps {
  mission: Mission;
  /** Leads its track: more room, and the full description rather than a kicker. */
  featured: boolean;
  /** Columns of the 12-wide grid this card occupies. */
  span: number;
}

/**
 * One mission panel.
 *
 * The event name dominates; everything else is instrument text around it. All
 * facts on the card come from the API — participation type, team size,
 * eligibility and availability are read through `domain/rules`, never inferred
 * here.
 *
 * Interaction is one real `<Link>` whose `::after` covers the panel, so a mouse
 * can click anywhere while the keyboard gets a single, correctly labelled stop.
 * It navigates to the event's own page rather than opening a panel in place:
 * every event is a real URL that can be linked, shared, opened in a tab and
 * returned to with the back button.
 *
 * Hover and focus states are pure CSS. A card grid is the last place that
 * should be running JavaScript on pointer move.
 */
export function MissionCard({ mission, featured, span }: MissionCardProps) {
  const { event, index } = mission;
  const identity = identityFor(event.eventId);
  const availability = availabilityOf(event);

  return (
    <article
      data-mission
      data-featured={featured}
      data-availability={availability}
      className="mission"
      style={{ '--span': span } as React.CSSProperties}
    >
      {/* Drawn on hover, from the left. The card activating, not decorating. */}
      <span data-mission-edge className="mission__edge" aria-hidden="true" />

      <header className="mission__head">
        <span className="mission__index" data-tabular aria-hidden="true">
          {String(index).padStart(2, '0')}
        </span>
        <span className="mission__codename">{identity.codename}</span>
      </header>

      <h4 className="mission__name">{event.name}</h4>

      <p className="mission__line">{featured ? event.description : identity.kicker}</p>

      <dl className="mission__meta">
        <div>
          <dt>Entry</dt>
          <dd>{participationLabel(event)}</dd>
        </div>
        <div>
          <dt>Roster</dt>
          <dd>{teamSizeLabel(event)}</dd>
        </div>
        <div>
          <dt>Eligible</dt>
          <dd>{eligibilityLabel(event)}</dd>
        </div>
      </dl>

      <footer className="mission__foot">
        <Link to={`/events/${event.eventId}`} className="mission__action">
          <span aria-hidden="true">[</span>
          <span className="mission__action-label">View mission</span>
          <span aria-hidden="true">]</span>
          {/* The accessible name has to identify WHICH mission — a screen reader
              running the link list would otherwise hear "View mission" seven
              times with nothing to tell them apart. */}
          <span className="sr-only">: {event.name}</span>
        </Link>

        <span className="mission__status" data-availability={availability}>
          <span className="mission__status-dot" aria-hidden="true" />
          {AVAILABILITY_LABEL[availability]}
        </span>
      </footer>
    </article>
  );
}
