import { useRegistrationState } from '@/hooks/useRegistrationState';

/**
 * The registration rule, stated once, above the board.
 *
 * One main event plus FIX IT. Putting it here rather than on every card is the
 * whole point: the structure is a property of the programme, not of any single
 * event, and repeating a warning seven times turns a simple rule into noise.
 *
 * When the device is recognised the slots fill in with what the student actually
 * holds, so the same strip doubles as a receipt. When it is not, it reads as the
 * plain statement of the rule it always was.
 */
export function RegistrationProtocol() {
  const state = useRegistrationState();

  const primaryFilled = state?.hasPrimaryEvent ?? false;
  const openFilled = state?.hasOpenEvent ?? false;

  return (
    <aside className="protocol" aria-label="Registration protocol">
      <p className="protocol__label">Registration slots</p>

      <dl className="protocol__slots">
        <div className="protocol__slot" data-filled={primaryFilled}>
          <dt>Primary</dt>
          <dd data-tabular>
            {primaryFilled ? state?.primaryEventName : '01'}
            <span className="protocol__mark" aria-hidden="true">
              {primaryFilled ? '✓' : '—'}
            </span>
          </dd>
        </div>

        <div className="protocol__slot" data-filled={openFilled}>
          <dt>FIX IT</dt>
          <dd data-tabular>
            {openFilled ? 'Registered' : '01'}
            <span className="protocol__mark" aria-hidden="true">
              {openFilled ? '✓' : '—'}
            </span>
          </dd>
        </div>
      </dl>

      <p className="protocol__note">
        {state === null
          ? 'One main event per student. FIX IT can be entered alongside it.'
          : primaryFilled && openFilled
            ? 'Both slots filled. You have reached the maximum.'
            : primaryFilled
              ? 'Your main event is set. FIX IT is still open to you.'
              : openFilled
                ? 'FIX IT is confirmed. You can still choose one main event.'
                : 'One main event per student. FIX IT can be entered alongside it.'}
      </p>
    </aside>
  );
}
