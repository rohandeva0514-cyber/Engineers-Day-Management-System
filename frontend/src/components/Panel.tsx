import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface PanelProps {
  children: ReactNode;
  className?: string;
  /** Adds the angular corner cut. Reserved for content that is its own object. */
  notched?: boolean;
  as?: 'div' | 'section' | 'article' | 'aside';
}

/**
 * A flat surface with a hairline border.
 *
 * Not a card: no radius, no shadow, no lift. Separation comes from the border and
 * the ground beneath it, which is what keeps a dense events grid from reading as a
 * SaaS dashboard.
 */
export function Panel({ children, className, notched = false, as: Tag = 'div' }: PanelProps) {
  return (
    <Tag className={cn('bg-panel border border-line', notched && 'clip-notch', className)}>
      {children}
    </Tag>
  );
}

/** Small uppercase monospace caption. The technical voice of the interface. */
export function TechLabel({
  children,
  className,
  bright = false,
}: {
  children: ReactNode;
  className?: string;
  bright?: boolean;
}) {
  return (
    <span className={cn('label-tech block', bright && 'label-tech-bright', className)}>
      {children}
    </span>
  );
}

/** A labelled value pair, used across event detail and the confirmation screen. */
export function DataField({
  label,
  children,
  className,
}: {
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('min-w-0', className)}>
      <TechLabel className="mb-1.5">{label}</TechLabel>
      <div className="text-[15px] text-ink leading-snug break-words">{children}</div>
    </div>
  );
}
