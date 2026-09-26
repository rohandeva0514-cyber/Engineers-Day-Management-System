import { cn } from '@/lib/cn';
import '@/styles/brand.css';

/**
 * Authorship.
 *
 * One component, two placements — the bottom of every practical page and the
 * closing frame of the cinematic route — so the site signs its name the same
 * way in both places and the wording cannot drift between them.
 *
 * Deliberately not a link: there is no destination to send anyone to, and a
 * dead link in a credit is worse than no link at all.
 */
interface CreditProps {
  /** `feature` is the closing colophon: centred, with more air around it. */
  variant?: 'inline' | 'feature';
  className?: string;
}

export function Credit({ variant = 'inline', className }: CreditProps) {
  return (
    <div className={cn('credit', variant === 'feature' && 'credit--feature', className)}>
      <p className="credit__label">
        <span className="credit__tag" aria-hidden="true">
          &lt;/&gt;
        </span>
        Designed &amp; built by
      </p>

      <p className="credit__name">Rohan Devadiga</p>

      <p className="credit__role">
        President{' '}
        <span className="credit__sep" aria-hidden="true">
          //
        </span>{' '}
        MIT Tech Kernel
      </p>
    </div>
  );
}
