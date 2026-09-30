import { useEffect, useState } from 'react';
import { ArenaFrame, ArenaLogLine, ArenaStatusLine } from '../components/ArenaFrame';
import { CodeCellInput } from '../components/CodeCellInput';
import type { ApiError } from '@/services/apiError';

/**
 * Check-in.
 *
 * Eight cells, one access code, one button. The whole screen has a single job and
 * is used once, quickly, by someone who may be standing.
 *
 * The verification log is chained to the real request rather than faked: if the
 * server answers in 90 ms the three lines still print over ~700 ms, because the
 * sequence reads as the system working rather than as a stall — but if the server
 * takes four seconds, the last line simply waits. It never claims to have finished
 * something that has not finished.
 */
export function AccessCodeScreen({
  onSubmit,
  busy,
  error,
}: {
  onSubmit: (code: string) => void;
  busy: boolean;
  error: ApiError | null;
}) {
  const [code, setCode] = useState('');
  const [secondLine, setSecondLine] = useState(false);

  // The first log line is derived from `busy` directly; only the second one needs a
  // timer. Resetting in the cleanup rather than in the effect body means there is no
  // synchronous state write on the way in — the effect starts a timer and nothing
  // else.
  useEffect(() => {
    if (!busy) return;
    const second = window.setTimeout(() => setSecondLine(true), 260);
    return () => {
      window.clearTimeout(second);
      setSecondLine(false);
    };
  }, [busy]);

  const complete = code.length === 8;

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!complete || busy) return;
    onSubmit(code);
  }

  return (
    <ArenaFrame tone={error === null ? 'active' : 'error'}>
      <ArenaStatusLine tone="active" label="Arena active" />
      <h1 className="arena__title">Debugging Arena</h1>
      <p className="arena__subtitle">Engineers&rsquo; Day 2026</p>

      <form onSubmit={submit} noValidate>
        <p className="arena__field-label">Enter access code</p>

        <CodeCellInput
          value={code}
          onChange={setCode}
          onComplete={() => {
            if (!busy) onSubmit(code);
          }}
          disabled={busy}
          invalid={error !== null}
        />

        <p className="arena__hint">
          Eight characters, issued when you registered for Debugging. Case does not
          matter.
        </p>

        <div className="arena__actions">
          <button
            type="submit"
            className="arena__button arena__button--primary"
            disabled={!complete || busy}
          >
            {busy ? 'Verifying…' : 'Verify access'}
          </button>
        </div>
      </form>

      {busy && (
        <>
          <div className="arena__scan" aria-hidden="true" />
          <div className="arena__log" aria-live="polite">
            <ArenaLogLine label="▸" value="Verifying access…" />
            {secondLine && <ArenaLogLine label="▸" value="Authenticating participant…" />}
          </div>
        </>
      )}

      {error !== null && !busy && (
        <div className="arena__log arena__log--error" role="alert">
          <ArenaLogLine label="▸" value={title(error)} />
          <p className="arena__error-detail">{error.message}</p>
        </div>
      )}
    </ArenaFrame>
  );
}

/**
 * One headline per refusal.
 *
 * `ACCESS_CODE_INVALID` covers an unknown code, a code for another event, and a
 * mistyped one — the backend does not distinguish them, and neither does this.
 */
function title(error: ApiError): string {
  switch (error.code) {
    case 'ACCESS_CODE_INVALID':
      return 'Access denied';
    case 'ARENA_OFFLINE':
      return 'Arena not open';
    case 'ARENA_ENDED':
      return 'Arena closed';
    case 'NETWORK':
    case 'UNAVAILABLE':
      return 'No signal';
    default:
      return 'Access refused';
  }
}
