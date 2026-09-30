import { ArenaFrame, ArenaLogLine, ArenaStatusLine } from '../components/ArenaFrame';
import type { AttemptState, AttemptStateResponse } from '@/services/arena/arenaTypes';

/**
 * The mission is over. One frame, three reasons.
 *
 * A student is never told their work was lost, because it was not: every terminal
 * path records the last thing they saved. What differs is why it ended, and that
 * difference matters — "you submitted", "time ran out" and "the organisers ended
 * the arena" are three different things to read, and merging them would leave
 * someone wondering whether they had made a mistake.
 *
 * No score, no rank, no breakdown. `RESULT PENDING` is the terminal state for a
 * participant; results are published by the organisers after evaluation.
 */
export function AttemptClosedScreen({ session }: { session: AttemptStateResponse }) {
  const { headline, reason } = copyFor(session.attempt.state);

  return (
    <ArenaFrame tone="ended">
      <ArenaStatusLine tone="ended" label="Mission complete" />
      <h1 className="arena__title arena__title--sm">{headline}</h1>
      <p className="arena__subtitle">Evaluation pending</p>

      <p className="arena__body">{reason}</p>
      <p className="arena__body">
        Your submission will be <strong>evaluated manually</strong> by the event
        organisers. Results are published after evaluation — you may close this
        window.
      </p>

      <div className="arena__log">
        <ArenaLogLine label="Participant" value={session.participant.fullName} />
        <ArenaLogLine label="Language" value={session.attempt.language ?? '—'} />
        <ArenaLogLine label="Status" value={session.attempt.state} />
      </div>
    </ArenaFrame>
  );
}

function copyFor(state: AttemptState): { headline: string; reason: string } {
  switch (state) {
    case 'EXPIRED':
      return {
        headline: 'Mission time expired',
        reason: 'Your 45 minutes are up. Everything you saved has been recorded.',
      };
    case 'TERMINATED':
      return {
        headline: 'Mission control has ended the arena',
        reason: 'The event has been closed by the organisers. Your work has been recorded.',
      };
    case 'SUBMITTED':
    default:
      return {
        headline: 'Submission recorded successfully',
        reason: 'Your saved code for every problem has been submitted. It is final.',
      };
  }
}
