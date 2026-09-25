import { NavLink, Link } from 'react-router-dom';
import { BrandLogo } from '@/components/BrandLogo';
import { SITE } from '@/data/siteContent';
import { cn } from '@/lib/cn';

const NAV = [
  { to: '/events', label: 'Events' },
  { to: '/my-registrations', label: 'My Registrations' },
] as const;

/**
 * Persistent navigation.
 *
 * Always present on every route, including — eventually — the cinematic drive.
 * CLAUDE.md is explicit that the cinematic sequence must never trap the user, and
 * the cheapest guarantee of that is a header that is never conditionally rendered.
 */
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-line bg-void/90 backdrop-blur-sm">
      {/* Thin HUD strip: deadline readout, always visible, never interactive. */}
      <div className="border-b border-line/60 bg-panel/50">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-1.5 sm:px-6">
          <span className="label-tech truncate">
            <span className="text-tech">◆</span> {SITE.org} / REGISTRATION TERMINAL
          </span>
          <span className="label-tech shrink-0" data-tabular>
            DEADLINE <span className="text-signal">{SITE.registrationDeadlineLabel}</span>
          </span>
        </div>
      </div>

      <div className="mx-auto flex max-w-7xl items-center justify-between gap-6 px-4 py-3.5 sm:px-6">
        {/* Institute mark on the left, community mark on the right, with the
            event name and navigation between them. The two flank the header
            rather than stacking, so neither reads as subordinate to the other. */}
        <Link to="/" className="group flex min-w-0 shrink items-center gap-3 sm:gap-4">
          <BrandLogo mark="institute" size="md" decorative className="shrink-0 transition-opacity group-hover:opacity-80" />

          {/* First thing to go when space is tight; the marks alone still
              identify the site. */}
          <span className="hidden h-14 w-px shrink-0 bg-line lg:block" aria-hidden="true" />
          <span className="hidden min-w-0 lg:block">
            <span className="block truncate font-display text-sm font-600 leading-none tracking-tight text-ink transition-colors group-hover:text-signal">
              ENGINEERS&rsquo; DAY
              <span className="text-signal"> 2026</span>
            </span>
          </span>
        </Link>

        <div className="flex min-w-0 items-center gap-2 sm:gap-4">
          <nav aria-label="Main">
            <ul className="flex items-center gap-1 sm:gap-2">
              {NAV.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    className={({ isActive }) =>
                      cn(
                        'block px-2.5 py-2 font-mono text-[11px] uppercase tracking-[0.12em] transition-colors sm:px-3',
                        isActive ? 'text-signal' : 'text-muted hover:text-ink',
                      )
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>

          <span className="hidden h-14 w-px shrink-0 bg-line sm:block" aria-hidden="true" />
          <Link to="/" className="group shrink-0" aria-label="MIT Tech Kernel">
            <BrandLogo
              mark="kernel"
              size="md"
              decorative
              className="transition-opacity group-hover:opacity-80"
            />
          </Link>
        </div>
      </div>
    </header>
  );
}
