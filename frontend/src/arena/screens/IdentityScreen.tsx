import { ArenaFrame, ArenaStatusLine } from '../components/ArenaFrame';
import type { ParticipantIdentity } from '@/services/arena/arenaTypes';

/**
 * "This is my registration."
 *
 * The only job of this screen is to let a participant recognise themselves before
 * they commit to anything. Five fields, all of them already known to the person
 * reading them — which is the point: nothing here would help someone who had
 * picked up another student's code.
 */
export function IdentityScreen({
  participant,
  onContinue,
}: {
  participant: ParticipantIdentity;
  onContinue: () => void;
}) {
  return (
    <ArenaFrame tone="active">
      <ArenaStatusLine tone="active" label="Access granted" />
      <h1 className="arena__title">{participant.fullName}</h1>
      <p className="arena__subtitle">Verified ✓</p>

      <dl className="arena__facts">
        <Fact label="Branch" value={participant.branch} />
        <Fact label="Year" value={ordinal(participant.yearLevel)} />
        <Fact label="Division" value={participant.division} />
        <Fact label="Event" value={participant.eventName} />
      </dl>

      <p className="arena__body">
        If this is not you, close this page and check your access code.
      </p>

      <div className="arena__actions">
        <button type="button" className="arena__button arena__button--primary" onClick={onContinue}>
          Continue
        </button>
      </div>
    </ArenaFrame>
  );
}

function Fact({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="arena__fact">
      <dt>{label}</dt>
      <dd>{value ?? '—'}</dd>
    </div>
  );
}

function ordinal(year: number): string {
  if (year === 1) return 'First year';
  if (year === 2) return 'Second year';
  return `Year ${year}`;
}
