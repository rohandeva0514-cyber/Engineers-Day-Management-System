import { BrandLockup } from '@/components/BrandLockup';
import { Credit } from '@/components/Credit';
import { SITE } from '@/data/siteContent';
import '@/styles/colophon.css';

/**
 * The closing frame of the cinematic route.
 *
 * The campaign needs somewhere to land. Without this it stops at the last
 * mission bay, which reads as a page that ran out rather than a sequence that
 * finished — and it leaves the three organisations behind the day with nowhere
 * to appear at a size worth looking at, since the HUD can only afford them as
 * chrome in the corner.
 *
 * No scroll timeline and no entrance. Everything above this moves; the one
 * frame that does not is what tells you the sequence is over.
 */
export function Colophon() {
  return (
    <footer className="colophon" aria-labelledby="colophon-title">
      <div className="colophon__rule" aria-hidden="true" />

      <p className="colophon__eyebrow">
        <span className="colophon__dot" aria-hidden="true" />
        End of line
      </p>

      <h2 id="colophon-title" className="colophon__title">
        Engineers&rsquo; Day <span className="colophon__year">2026</span>
      </h2>

      <p className="colophon__label">Presented by</p>
      <BrandLockup size="lg" className="colophon__marks" />

      <Credit variant="feature" />

      <p className="colophon__legal">
        {SITE.org}
        <span aria-hidden="true">·</span>
        MAEER&rsquo;s MIT, Thane
        <span aria-hidden="true">·</span>
        Registration closes {SITE.registrationDeadlineLabel}
      </p>
    </footer>
  );
}
