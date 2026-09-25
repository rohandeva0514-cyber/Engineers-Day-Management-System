import { useLocation } from 'react-router-dom';
import type { RegistrationReceipt } from '@/domain/types';
import { ButtonLink } from '@/components/Button';
import { PageMessage } from '@/components/ErrorState';
import { DataField, TechLabel } from '@/components/Panel';
import { SITE, identityFor } from '@/data/siteContent';
import { formatDateTime, pad2 } from '@/lib/format';

/**
 * Registration confirmed.
 *
 * Built to read like an issued clearance rather than a success toast: the
 * registration ids are the thing a student will be asked for at the desk, so they
 * are set in mono at a size you can read off a phone held at arm's length.
 *
 * It stays a practical document — real headings, real lists, selectable text, no
 * canvas, no animation required to understand it. The spectacle is in the framing,
 * not in anything load-bearing.
 */
export function RegistrationSuccessPage() {
  const location = useLocation();
  const receipt = (location.state as { receipt?: RegistrationReceipt } | null)?.receipt;

  // Reached directly, or refreshed — router state does not survive either.
  if (receipt === undefined) {
    return (
      <div className="mx-auto max-w-3xl px-4 sm:px-6">
        <PageMessage
          label="No receipt in this session"
          title="Nothing to show here"
          action={
            <>
              <ButtonLink to="/my-registrations">My registrations</ButtonLink>
              <ButtonLink to="/events" variant="ghost">
                All events
              </ButtonLink>
            </>
          }
        >
          <p>
            This page shows a confirmation right after you register. If you have already
            registered, your registrations are listed under <strong>My Registrations</strong>.
          </p>
        </PageMessage>
      </div>
    );
  }

  const identity = identityFor(receipt.eventId);
  const isTeam = receipt.team !== null;

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
      {/* Clearance header. The notch and the accent rail carry the identity; the
          content underneath stays a plain, readable document. */}
      <section
        aria-labelledby="confirmation-heading"
        className="clip-notch relative border border-ok/40 bg-panel anim-rise"
      >
        <span
          aria-hidden="true"
          className="absolute left-0 top-0 h-full w-[3px]"
          style={{ backgroundColor: identity.accent }}
        />

        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-3 pl-6">
          <TechLabel className="text-ok">◆ Registration confirmed</TechLabel>
          <TechLabel>{SITE.event}</TechLabel>
        </div>

        <div className="px-5 py-6 pl-6 sm:px-8 sm:py-8">
          <TechLabel bright className="mb-2">
            {identity.codename}
          </TechLabel>

          <h1 id="confirmation-heading" className="font-display text-3xl text-ink sm:text-4xl">
            {receipt.eventName}
          </h1>

          <p className="mt-3 text-sm text-muted">
            {isTeam
              ? `Your team is registered. All ${receipt.registrations.length} members are confirmed.`
              : 'You are registered for this event.'}
          </p>

          <dl className="mt-7 grid grid-cols-1 gap-5 border-t border-line pt-6 sm:grid-cols-3">
            <DataField label="Entry type">{receipt.participationType}</DataField>
            {receipt.team !== null && <DataField label="Team">{receipt.team.name}</DataField>}
            <DataField label="Registered at">{formatDateTime(receipt.registeredAt)}</DataField>
          </dl>
        </div>
      </section>

      {/* The roster, with the identifiers that matter operationally. */}
      <section aria-labelledby="roster-heading" className="mt-8">
        <div className="mb-3 flex items-baseline justify-between gap-4">
          <h2 id="roster-heading" className="text-lg text-ink">
            {isTeam ? 'Registered members' : 'Registered participant'}
          </h2>
          <TechLabel data-tabular>{receipt.registrations.length} TOTAL</TechLabel>
        </div>

        <ul className="border border-line">
          {receipt.registrations.map((entry, index) => (
            <li
              key={entry.registrationId}
              className="flex flex-col gap-3 border-b border-line bg-panel px-4 py-3.5 last:border-b-0 sm:flex-row sm:items-center sm:justify-between sm:px-5"
            >
              <div className="flex min-w-0 items-center gap-4">
                <span className="label-tech w-5 shrink-0" data-tabular>
                  {pad2(index + 1)}
                </span>
                <div className="min-w-0">
                  <p className="truncate text-[15px] text-ink">
                    {entry.participant.fullName}
                    {isTeam && index === 0 && (
                      <span className="label-tech ml-2 inline">· captain</span>
                    )}
                  </p>
                  <p className="label-tech mt-0.5 truncate">
                    {entry.participant.rollNo} · year {entry.participant.yearLevel}
                  </p>
                </div>
              </div>

              {/* The number they will be asked for. Selectable, tabular, prominent. */}
              <div className="shrink-0 sm:text-right">
                <TechLabel className="sm:text-right">Registration ID</TechLabel>
                <p className="font-mono text-base text-signal" data-tabular>
                  #{entry.registrationId}
                </p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="next-heading" className="mt-8 border border-line bg-panel px-5 py-5 sm:px-6">
        <TechLabel className="mb-2">What happens next</TechLabel>
        <h2 id="next-heading" className="text-lg text-ink">
          Keep your registration ID
        </h2>
        <ul className="mt-3 space-y-2 text-sm leading-relaxed text-muted">
          <li className="flex gap-3">
            <span className="text-signal" aria-hidden="true">
              →
            </span>
            <span>
              Note your registration ID. You can find it again at any time under{' '}
              <strong className="text-ink">My Registrations</strong>.
            </span>
          </li>
          <li className="flex gap-3">
            <span className="text-signal" aria-hidden="true">
              →
            </span>
            <span>
              {isTeam
                ? 'Let your team know they are registered — every member is confirmed under this team.'
                : 'Event timing and venue details will be published before the event.'}
            </span>
          </li>
          <li className="flex gap-3">
            <span className="text-signal" aria-hidden="true">
              →
            </span>
            <span>
              Event workspaces open on the day, when the organisers start the event. There is
              nothing further to do until then.
            </span>
          </li>
        </ul>
      </section>

      <div className="mt-8 flex flex-wrap gap-3">
        <ButtonLink to="/my-registrations">My registrations</ButtonLink>
        <ButtonLink to="/events" variant="secondary">
          Register for another event
        </ButtonLink>
      </div>
    </div>
  );
}
