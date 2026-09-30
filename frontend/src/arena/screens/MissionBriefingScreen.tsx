import { ArenaFrame, ArenaStatusLine } from '../components/ArenaFrame';
import type { ArenaLanguageOption } from '@/services/arena/arenaTypes';
import type { ApiError } from '@/services/apiError';

/**
 * The last screen before the clock starts.
 *
 * Written to be read by a first-year student under mild pressure, so: short lines,
 * no military metaphors, and the rules stated as facts rather than as warnings.
 *
 * The one sentence that must not be missed — that this button starts the timer —
 * sits on the button itself rather than in a paragraph above it, where it would be
 * skimmed past.
 */
export function MissionBriefingScreen({
  language,
  durationSeconds,
  onStart,
  onBack,
  busy,
  error,
}: {
  language: ArenaLanguageOption | null;
  durationSeconds: number;
  onStart: () => void;
  onBack: () => void;
  busy: boolean;
  error: ApiError | null;
}) {
  const minutes = Math.round(durationSeconds / 60) || 45;

  return (
    <ArenaFrame tone="active">
      <ArenaStatusLine tone="active" label="Step 2 of 2 · System online" />
      <h1 className="arena__title arena__title--sm">Mission briefing</h1>

      <dl className="arena__facts">
        <div className="arena__fact">
          <dt>Language</dt>
          <dd>{language?.label ?? '—'}</dd>
        </div>
        <div className="arena__fact">
          <dt>Time</dt>
          <dd>{minutes} minutes</dd>
        </div>
        <div className="arena__fact">
          <dt>Zones</dt>
          <dd>Easy · Medium · Hard</dd>
        </div>
        <div className="arena__fact">
          <dt>Problems</dt>
          <dd>4 in each zone</dd>
        </div>
      </dl>

      <p className="arena__body">
        You have been given a set of programs that do not work. Find the faults,
        repair the code, and pass the hidden tests.
      </p>

      <ul className="arena__rules">
        <li>
          Move between zones and problems in <strong>any order, at any time</strong>.
          Nothing is locked behind anything else.
        </li>
        <li>Your code is saved automatically as you work.</li>
        <li>
          You can submit whenever you are ready.{' '}
          <strong>Submitting ends the mission for you and cannot be undone.</strong>
        </li>
        <li>When the time runs out, whatever you have saved is recorded.</li>
      </ul>

      {error !== null && (
        <p className="arena__error-detail" role="alert">
          {error.message}
        </p>
      )}

      <div className="arena__actions">
        <button
          type="button"
          className="arena__button arena__button--primary"
          disabled={busy || language === null}
          onClick={onStart}
        >
          {busy ? 'Starting…' : `Start mission — begins your ${minutes} minutes`}
        </button>
        <button type="button" className="arena__button" disabled={busy} onClick={onBack}>
          Change language
        </button>
      </div>
    </ArenaFrame>
  );
}
