import type { Event } from '@/domain/types';
import { capacityFraction, capacityUnitLabel, hasPublishedCapacity, isScarce } from '@/domain/rules';
import { TechLabel } from '@/components/Panel';

/**
 * Seat availability.
 *
 * The important case is the one with no capacity at all. `capacity: null` is normal
 * — most events close manually, and Ideathon's limit is not finalised — so this
 * renders "no published limit" rather than a zero, a dash, or an invented number.
 * Showing a fake cap would be worse than showing nothing: a student would plan
 * around it.
 */
export function CapacityMeter({ event, size = 'md' }: { event: Event; size?: 'sm' | 'md' }) {
  if (!hasPublishedCapacity(event)) {
    return (
      <div>
        <TechLabel className="mb-1.5">Capacity</TechLabel>
        <p className="text-sm text-muted">
          No published limit
          <span className="mt-0.5 block text-xs text-faint">Closes at the organisers&rsquo; discretion</span>
        </p>
      </div>
    );
  }

  const capacity = event.capacity ?? 0;
  const remaining = event.seatsRemaining ?? 0;
  const fraction = capacityFraction(event) ?? 0;
  const scarce = isScarce(event);
  const unit = capacityUnitLabel(event, remaining);

  return (
    <div>
      <TechLabel className="mb-1.5">Capacity</TechLabel>

      <p className={size === 'sm' ? 'text-sm' : 'text-base'} data-tabular>
        <span className={scarce ? 'text-signal font-semibold' : 'text-ink font-semibold'}>
          {remaining}
        </span>
        <span className="text-muted"> of {capacity} {unit} left</span>
      </p>

      {/* Segmented rather than a smooth bar — it reads as an instrument, and the
          segments make small differences legible at a glance. */}
      <div
        className="mt-2 flex h-1.5 gap-px"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={capacity}
        aria-valuenow={event.seatsTaken ?? 0}
        aria-label={`${remaining} of ${capacity} ${unit} remaining`}
      >
        {Array.from({ length: 20 }, (_, index) => {
          const filled = index / 20 < fraction;
          return (
            <span
              key={index}
              className={
                filled ? (scarce ? 'flex-1 bg-signal' : 'flex-1 bg-tech') : 'flex-1 bg-line'
              }
            />
          );
        })}
      </div>
    </div>
  );
}
