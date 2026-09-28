import { useRegistrationState } from '@/hooks/useRegistrationState';
import { freeSlots } from '@/domain/rules';
import type { SlotState } from '@/domain/types';

/**
 * The registration rule, stated once, above the board.
 *
 * Three events: one from each registration group. Putting it here rather than on
 * every card is the whole point — the structure is a property of the programme,
 * not of any single event, and repeating a warning seven times turns a simple
 * rule into noise.
 *
 * When the device is recognised the slots fill in with what the student actually
 * holds, so the same strip doubles as a receipt. When it is not, it reads as the
 * plain statement of the rule it always was.
 *
 * The groups come from the server: their number, order and names are all read
 * off `registrationState.slots`, so a fourth group would appear here without an
 * edit. Before any registration exists there is no state to read, so the
 * placeholder row below is the one piece of structure this file knows on its own.
 */
export function RegistrationProtocol() {
  const state = useRegistrationState();
  const slots = state?.slots ?? null;

  return (
    <aside className="protocol" aria-label="Registration protocol">
      <p className="protocol__label">Registration slots</p>

      <dl className="protocol__slots">
        {slots === null ? (
          <PlaceholderSlots />
        ) : (
          slots.map((slot) => <Slot key={slot.slot} slot={slot} />)
        )}
      </dl>

      <p className="protocol__note">{noteFor(slots)}</p>
    </aside>
  );
}

function Slot({ slot }: { slot: SlotState }) {
  return (
    <div className="protocol__slot" data-filled={slot.taken}>
      <dt>{slot.label}</dt>
      <dd data-tabular>
        {slot.taken ? slot.eventName : '01'}
        <span className="protocol__mark" aria-hidden="true">
          {slot.taken ? '✓' : '—'}
        </span>
      </dd>
    </div>
  );
}

/**
 * The strip before the server has anything to say about this device.
 *
 * A count, not the group names: naming them would mean hardcoding the three the
 * backend currently defines, and a strip that quietly went stale would be worse
 * than one that simply shows the shape of the rule.
 */
function PlaceholderSlots() {
  return (
    <div className="protocol__slot" data-filled={false}>
      <dt>Events</dt>
      <dd data-tabular>
        03
        <span className="protocol__mark" aria-hidden="true">
          —
        </span>
      </dd>
    </div>
  );
}

function noteFor(slots: SlotState[] | null): string {
  const rule = 'Three events per student — one from each group.';
  if (slots === null) return rule;

  const free = freeSlots({ slots });
  if (free.length === 0) return 'Every slot filled. You have reached the maximum.';

  const remaining = free.map((slot) => slot.label).join(', ');
  return free.length === slots.length
    ? rule
    : `Still open to you: ${remaining}.`;
}
