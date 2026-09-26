import { BrandLogo, type LogoSize } from './BrandLogo';
import { cn } from '@/lib/cn';
import '@/styles/brand.css';

/**
 * The three organisations behind the day.
 *
 * The college on one side; the two communities running the event grouped on the
 * other. That is the same arrangement the site header uses, so the page opens
 * and closes on the same composition rather than mirroring itself.
 *
 * Grouping is not ranking: MIT TECH KERNEL runs Engineers' Day and is the
 * largest mark in every lockup regardless of where it sits. That scale is not
 * set here — it comes from BrandLogo's per-mark size table, so a change of rank
 * is a one-line change in one file.
 */
interface BrandLockupProps {
  /** `lg` is a standalone brand moment; `md` is the footer strip. */
  size?: Extract<LogoSize, 'md' | 'lg'>;
  /**
   * `split` pushes the two groups to opposite ends of the available width — the
   * footer band, where the row is the full page measure.
   *
   * `inline` keeps them adjacent, for a lockup that is being centred or sitting
   * inside a narrower column.
   */
  layout?: 'inline' | 'split';
  className?: string;
}

export function BrandLockup({ size = 'md', layout = 'inline', className }: BrandLockupProps) {
  return (
    <div className={cn('brand-lockup', layout === 'split' && 'brand-lockup--split', className)}>
      <BrandLogo mark="institute" size={size} />

      {/* Hairline between the college and the two communities. Only earns its
          place when the groups sit next to each other — pushed to opposite ends
          of a wide row it would float in the middle with nothing to separate. */}
      {layout === 'inline' && <span className="brand-lockup__rule" aria-hidden="true" />}

      <div className="brand-lockup__partners">
        <BrandLogo mark="kernel" size={size} />
        <BrandLogo mark="ecell" size={size} />
      </div>
    </div>
  );
}
