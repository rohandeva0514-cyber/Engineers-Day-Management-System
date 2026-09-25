import type { ReactNode } from 'react';
import { ApiError, ERROR_HINTS, ERROR_TITLES } from '@/services/apiError';
import { Button, ButtonLink } from './Button';
import { TechLabel } from './Panel';
import { cn } from '@/lib/cn';

/**
 * The single place an API failure becomes something a student reads.
 *
 * Three rules hold everywhere:
 *  1. No stack traces, no status codes as headlines, no raw backend prose without
 *     a human sentence around it.
 *  2. Every state says what happened AND what to do next.
 *  3. When nothing was submitted, say so — a failed registration attempt is far
 *     less alarming once you know you are not half-registered.
 */

interface ErrorStateProps {
  error: ApiError;
  /** Retry handler. Offered only for failures where retrying makes sense. */
  onRetry?: (() => void) | undefined;
  /** Shown alongside retry for event-state problems. */
  backTo?: { to: string; label: string } | undefined;
  className?: string;
  compact?: boolean;
}

export function ErrorState({ error, onRetry, backTo, className, compact = false }: ErrorStateProps) {
  const title = ERROR_TITLES[error.code] ?? ERROR_TITLES.UNKNOWN;

  // The backend writes its messages to be shown, and they are usually more specific
  // than anything generic — they name the roll numbers or the required team size.
  const detail = error.message !== '' ? error.message : ERROR_HINTS[error.code];
  const hint = ERROR_HINTS[error.code];
  const showHintSeparately = hint !== undefined && hint !== detail;

  return (
    <div
      role="alert"
      className={cn(
        'border border-danger/40 bg-danger-soft/40',
        compact ? 'p-4' : 'p-6 sm:p-8',
        className,
      )}
    >
      <TechLabel className="text-danger mb-2">{error.code.replace(/_/g, ' ')}</TechLabel>

      <h2 className={cn('text-ink font-display', compact ? 'text-lg' : 'text-xl sm:text-2xl')}>
        {title}
      </h2>

      {detail !== undefined && <p className="mt-2 text-sm text-muted max-w-prose">{detail}</p>}
      {showHintSeparately && <p className="mt-2 text-sm text-muted max-w-prose">{hint}</p>}

      <FieldErrorList error={error} />

      {(onRetry !== undefined || backTo !== undefined) && (
        <div className="mt-5 flex flex-wrap gap-3">
          {onRetry !== undefined && error.isTransient && (
            <Button variant="secondary" onClick={onRetry}>
              Try again
            </Button>
          )}
          {backTo !== undefined && (
            <ButtonLink to={backTo.to} variant="ghost">
              {backTo.label}
            </ButtonLink>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Per-field failures from a 400.
 *
 * Rendered as a summary list at the top of the form as well as inline on each
 * input, because a student who tabbed past an error needs to find it without
 * hunting down a ten-member roster.
 */
function FieldErrorList({ error }: { error: ApiError }) {
  const fieldErrors = Object.entries(error.fieldErrors);
  if (fieldErrors.length === 0) return null;

  return (
    <ul className="mt-4 space-y-1.5 border-l-2 border-danger/50 pl-4">
      {fieldErrors.map(([field, message]) => (
        <li key={field} className="text-sm text-muted">
          <span className="font-mono text-xs text-danger">{humaniseField(field)}</span>
          <span className="mx-2 text-faint">—</span>
          {message}
        </li>
      ))}
    </ul>
  );
}

/** `participants[0].email` becomes `Member 1 · email`. */
function humaniseField(field: string): string {
  const match = /^participants\[(\d+)]\.(.+)$/.exec(field);
  if (match !== null) {
    const index = Number(match[1]) + 1;
    return `Member ${index} · ${match[2]}`;
  }
  return field;
}

/* ------------------------------------------------------------------ states */

/** Full-page empty/loading/not-found scaffold, so those states look designed. */
export function PageMessage({
  label,
  title,
  children,
  action,
}: {
  label: string;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="py-24 text-center">
      <TechLabel className="mb-3">{label}</TechLabel>
      <h1 className="text-2xl sm:text-3xl text-ink">{title}</h1>
      {children !== undefined && (
        <div className="mt-3 text-sm text-muted max-w-md mx-auto">{children}</div>
      )}
      {action !== undefined && <div className="mt-7 flex justify-center gap-3">{action}</div>}
    </div>
  );
}
