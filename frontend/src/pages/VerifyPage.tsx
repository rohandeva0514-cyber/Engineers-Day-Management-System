import { useState } from 'react';
import { TextField } from '@/components/Field';
import { Button } from '@/components/Button';
import { ErrorState } from '@/components/ErrorState';
import { TechLabel } from '@/components/Panel';
import { ApiError } from '@/services/apiError';
import { verifyAccessCode } from '@/services/verificationApi';
import { ordinalYear } from '@/domain/rules';
import type { AccessCodeVerification } from '@/domain/types';

/**
 * Event-day check-in.
 *
 * A student presents the access ID they were given at registration; the server
 * resolves it and returns just enough to confirm the right person is at the
 * terminal. Everything shown here comes from that response — there is no local
 * lookup, no cached identity, and nothing derived from the code itself.
 *
 * A deliberately plain screen. It gets used once, quickly, by someone standing
 * up, possibly on a shared machine.
 */
export function VerifyPage() {
  const [code, setCode] = useState('');
  const [result, setResult] = useState<AccessCodeVerification | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (code.trim() === '') return;

    setBusy(true);
    setError(null);
    setResult(null);

    try {
      setResult(await verifyAccessCode(code));
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Verification failed.'),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-10 sm:px-6 sm:py-14">
      <header className="border-b border-line pb-6">
        <TechLabel bright className="mb-3">
          Event-day check-in
        </TechLabel>
        <h1 className="font-display text-3xl leading-tight text-ink sm:text-4xl">
          Verify participant
        </h1>
        <p className="mt-3 max-w-prose text-sm leading-relaxed text-muted">
          Enter the access ID issued when you registered. It was shown on your
          confirmation screen and is listed under My Registrations.
        </p>
      </header>

      <form onSubmit={submit} noValidate className="pt-8">
        <TextField
          label="Access ID"
          required
          autoComplete="off"
          spellCheck={false}
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder="K4M2X9QT"
          hint="Eight characters. Case does not matter."
          wrapperClassName="max-w-xs"
        />

        <div className="mt-6">
          <Button type="submit" size="lg" disabled={busy || code.trim() === ''}>
            {busy ? 'Checking…' : 'Verify participant'}
          </Button>
        </div>
      </form>

      {error !== null && (
        <div className="mt-8">
          <ErrorState error={error} compact />
        </div>
      )}

      {result !== null && (
        <section
          aria-labelledby="verified-heading"
          className="clip-notch-tr mt-8 border border-ok/40 bg-ok-soft/30 px-5 py-6 sm:px-6"
        >
          <TechLabel className="mb-2 text-ok">Participant verified</TechLabel>
          <h2 id="verified-heading" className="font-display text-2xl text-ink">
            {result.fullName}
          </h2>

          <dl className="mt-5 grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
            <Fact label="Event" value={result.eventName} />
            <Fact label="Status" value={result.registrationStatus} />
            <Fact label="Branch" value={result.branch} />
            <Fact label="Division" value={result.division} />
            <Fact label="Year" value={`${ordinalYear(result.yearLevel)} year`} />
          </dl>
        </section>
      )}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="label-tech">{label}</dt>
      <dd className="mt-1 text-[15px] text-ink">{value ?? '—'}</dd>
    </div>
  );
}
