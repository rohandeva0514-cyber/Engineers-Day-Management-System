import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { ParticipantRegistrations } from '@/domain/types';
import { identitySession } from '@/services/identitySession';
import { ApiError } from '@/services/apiError';
import { ErrorState } from '@/components/ErrorState';
import { Spinner } from '@/components/Spinner';
import { Button, ButtonLink } from '@/components/Button';
import { TextField } from '@/components/Field';
import { DataField, TechLabel } from '@/components/Panel';
import { formatDateTime, pad2 } from '@/lib/format';

/**
 * A participant's own registrations.
 *
 * There is no authentication on the backend yet, and this page does not pretend
 * otherwise. It goes through `identitySession` for everything, so when email OTP or
 * SSO lands the implementation changes there and nothing here does.
 *
 * Two routes in, in order of preference:
 *  1. The device remembers a registration completed in this browser — no typing.
 *  2. A roll-number lookup, clearly marked as temporary. It exists because a
 *     student who registered on a shared lab machine has no other way back to
 *     their record, and it is the first thing that should sit behind a real
 *     verification challenge.
 */
export function MyRegistrationsPage() {
  const [data, setData] = useState<ParticipantRegistrations | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [rollNo, setRollNo] = useState('');

  const remembered = identitySession.current();

  const load = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await identitySession.loadOwnRegistrations(signal);
      if (result !== null) setData(result);
    } catch (cause) {
      if (signal?.aborted === true) return;
      setError(cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Could not load.'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Self-populate when this device has a remembered identity.
  useEffect(() => {
    if (remembered === null) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [remembered?.participantId, load]);

  async function handleLookup(formEvent: React.FormEvent) {
    formEvent.preventDefault();
    if (rollNo.trim() === '') return;

    setIsLoading(true);
    setError(null);
    try {
      const result = await identitySession.lookupByRollNo(rollNo);
      setData(result);
    } catch (cause) {
      setData(null);
      setError(cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Lookup failed.'));
    } finally {
      setIsLoading(false);
    }
  }

  function handleSignOut() {
    identitySession.clear();
    setData(null);
    setError(null);
    setRollNo('');
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-10 sm:px-6 sm:py-14">
      <header className="border-b border-line pb-6">
        <TechLabel bright className="mb-3">
          Participant record
        </TechLabel>
        <h1 className="text-3xl text-ink sm:text-4xl">My Registrations</h1>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted">
          Every event you are registered for, with the registration IDs you will be asked for on
          the day.
        </p>
      </header>

      {/* Lookup. Shown when the device has no remembered identity, or as a way to
          check a different record. */}
      {data === null && (
        <section aria-labelledby="lookup-heading" className="mt-8 border border-line bg-panel p-5 sm:p-6">
          <h2 id="lookup-heading" className="text-lg text-ink">
            Find your registrations
          </h2>
          <p className="mt-2 max-w-xl text-sm text-muted">
            Enter the roll number you registered with.
          </p>

          <form onSubmit={handleLookup} className="mt-5 flex flex-col gap-4 sm:flex-row sm:items-end">
            <TextField
              label="Roll number"
              value={rollNo}
              onChange={(e) => setRollNo(e.target.value)}
              placeholder="1MS24CS001"
              autoComplete="off"
              spellCheck={false}
              wrapperClassName="sm:max-w-xs sm:flex-1"
            />
            <Button type="submit" disabled={isLoading || rollNo.trim() === ''}>
              {isLoading ? 'Looking up…' : 'Look up'}
            </Button>
          </form>

          <p className="mt-5 border-l-2 border-line-bright pl-4 text-xs leading-relaxed text-faint">
            <span className="label-tech mb-1 block">Temporary mechanism</span>
            Registrations are not yet protected by a login. This lookup will move behind an email
            verification step before the event.
          </p>
        </section>
      )}

      {isLoading && data === null && <Spinner label="Loading registrations" className="mt-8" />}

      {error !== null && (
        <div className="mt-8">
          {error.code === 'PARTICIPANT_NOT_FOUND' ? (
            <div className="border border-line bg-panel p-6">
              <TechLabel className="mb-2">No record</TechLabel>
              <h2 className="text-lg text-ink">No registrations found</h2>
              <p className="mt-2 max-w-xl text-sm text-muted">
                Nothing is registered under that roll number. Check the spelling, or register for
                an event first.
              </p>
              <ButtonLink to="/events" variant="secondary" className="mt-5">
                Browse events
              </ButtonLink>
            </div>
          ) : (
            <ErrorState error={error} onRetry={() => void load()} />
          )}
        </div>
      )}

      {data !== null && (
        <section aria-labelledby="record-heading" className="mt-8 space-y-6">
          <div className="border border-line bg-panel p-5 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <TechLabel className="mb-2">Registered as</TechLabel>
                <h2 id="record-heading" className="font-display text-2xl text-ink">
                  {data.participant.fullName}
                </h2>
              </div>
              <Button variant="ghost" onClick={handleSignOut} className="shrink-0">
                Switch record
              </Button>
            </div>

            <dl className="mt-5 grid grid-cols-2 gap-5 border-t border-line pt-5 sm:grid-cols-4">
              <DataField label="Roll number">{data.participant.rollNo}</DataField>
              <DataField label="Year">{data.participant.yearLevel}</DataField>
              <DataField label="Email" className="col-span-2">
                <span className="break-all">{data.participant.email}</span>
              </DataField>
            </dl>
          </div>

          {data.registrations.length === 0 ? (
            <div className="border border-line bg-panel p-6 text-center">
              <TechLabel className="mb-2">Empty</TechLabel>
              <p className="text-ink">No events registered yet.</p>
              <ButtonLink to="/events" className="mt-5">
                Browse events
              </ButtonLink>
            </div>
          ) : (
            <div>
              <div className="mb-3 flex items-baseline justify-between gap-4">
                <h3 className="text-lg text-ink">Registered events</h3>
                <TechLabel data-tabular>{data.registrations.length} TOTAL</TechLabel>
              </div>

              <ul className="border border-line">
                {data.registrations.map((registration, index) => (
                  <li
                    key={registration.registrationId}
                    className="border-b border-line bg-panel last:border-b-0"
                  >
                    <div className="flex flex-col gap-4 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
                      <div className="flex min-w-0 items-center gap-4">
                        <span className="label-tech w-5 shrink-0" data-tabular>
                          {pad2(index + 1)}
                        </span>
                        <div className="min-w-0">
                          <Link
                            to={`/events/${registration.eventId}`}
                            className="block truncate font-display text-lg text-ink transition-colors hover:text-signal"
                          >
                            {registration.eventName}
                          </Link>
                          <p className="label-tech mt-0.5 truncate">
                            {registration.participationType}
                            {registration.teamName !== null && ` · ${registration.teamName}`}
                            {' · '}
                            {formatDateTime(registration.registeredAt)}
                          </p>
                        </div>
                      </div>

                      <div className="shrink-0 sm:text-right">
                        <TechLabel className="sm:text-right">Registration ID</TechLabel>
                        <p className="font-mono text-base text-signal" data-tabular>
                          #{registration.registrationId}
                        </p>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
