import { cn } from '@/lib/cn';

/**
 * Loading indicator.
 *
 * A square that rotates rather than a circular spinner — consistent with the
 * angular geometry, and it reads as "system working" rather than "web page".
 * Carries the accessible text itself so callers cannot forget it.
 */
export function Spinner({ label = 'Loading', className }: { label?: string; className?: string }) {
  return (
    <span role="status" className={cn('inline-flex items-center gap-3', className)}>
      <span
        aria-hidden="true"
        className="size-3 border-2 border-signal border-t-transparent animate-spin"
      />
      <span className="label-tech">{label}</span>
    </span>
  );
}

/** Skeleton block for content that has a known shape before it arrives. */
export function SkeletonRow({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('bg-raised animate-pulse', className)} />;
}
