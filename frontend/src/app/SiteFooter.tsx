import { Link } from 'react-router-dom';
import { BrandLockup } from '@/components/BrandLockup';
import { Credit } from '@/components/Credit';
import { SITE } from '@/data/siteContent';

/**
 * The foot of every practical page.
 *
 * Three bands, coarse to fine: who is putting the day on, where to go next, and
 * who built the thing you are looking at. The marks lead rather than sit in a
 * corner — on the practical routes this is the one place all three appear at a
 * size worth looking at, since the header can only afford them as chrome.
 */
export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-line bg-panel/30">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 sm:py-12">
        {/* --- band 1: the organisations --------------------------------- */}
        <p className="label-tech">
          <span className="text-tech">◆</span> Presented by
        </p>
        {/* Split across the full measure: the college on the left, the two
            communities running the event together on the right — the same
            arrangement as the header, so the page closes the way it opened. */}
        <BrandLockup size="md" layout="split" className="mt-5" />

        {/* --- band 2: name, and where to go next ------------------------ */}
        <div className="mt-10 flex flex-col gap-4 border-t border-line/70 pt-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-display text-sm text-ink">{SITE.org}</p>
            <p className="label-tech mt-1">{SITE.event}</p>
          </div>

          <nav aria-label="Footer">
            <ul className="flex flex-wrap gap-x-5 gap-y-2">
              <li>
                <Link to="/events" className="label-tech transition-colors hover:text-ink">
                  Events
                </Link>
              </li>
              <li>
                <Link to="/my-registrations" className="label-tech transition-colors hover:text-ink">
                  My Registrations
                </Link>
              </li>
            </ul>
          </nav>
        </div>

        {/* --- band 3: authorship ----------------------------------------
            Below the navigation, not beside it: a signature belongs at the end
            of the page, after everything the page was actually for. */}
        <div className="mt-8 flex flex-col gap-5 border-t border-line/70 pt-7 sm:flex-row sm:items-end sm:justify-between">
          <Credit />

          <p className="label-tech sm:text-right" data-tabular>
            © 2026 MIT Tech Kernel
            <span className="mx-2 text-line-bright" aria-hidden="true">
              |
            </span>
            Registration closes {SITE.registrationDeadlineLabel}
          </p>
        </div>
      </div>
    </footer>
  );
}
