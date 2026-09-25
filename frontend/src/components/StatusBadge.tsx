import { cn } from '@/lib/cn';
import { AVAILABILITY_LABEL } from '@/domain/rules';
import type { EventAvailability } from '@/domain/types';

/**
 * Registration status.
 *
 * Status is never carried by colour alone — each state has a distinct label and a
 * distinct indicator shape, so it survives being read on a monochrome screen or by
 * someone who cannot separate the hues. The colours are semantic tokens, kept
 * deliberately separate from the brand amber so "open" never looks like "button".
 */
const STYLES: Record<EventAvailability, { wrap: string; dot: string }> = {
  OPEN: {
    wrap: 'text-ok border-ok/40 bg-ok-soft',
    dot: 'bg-ok',
  },
  SOLD_OUT: {
    wrap: 'text-danger border-danger/40 bg-danger-soft',
    dot: 'bg-danger',
  },
  CLOSED: {
    wrap: 'text-closed border-line-bright bg-raised',
    dot: 'bg-closed',
  },
};

interface StatusBadgeProps {
  status: EventAvailability;
  className?: string;
  size?: 'sm' | 'md';
}

export function StatusBadge({ status, className, size = 'sm' }: StatusBadgeProps) {
  const style = STYLES[status];

  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 border font-mono uppercase tracking-[0.14em] whitespace-nowrap',
        size === 'sm' ? 'px-2 py-[3px] text-[10px]' : 'px-3 py-1.5 text-[11px]',
        style.wrap,
        className,
      )}
    >
      {/* A square, not a circle — the geometry stays angular throughout. */}
      <span aria-hidden="true" className={cn('size-[5px]', style.dot)} />
      {AVAILABILITY_LABEL[status]}
    </span>
  );
}
