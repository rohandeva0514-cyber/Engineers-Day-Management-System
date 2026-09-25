import { NavLink, Link } from 'react-router-dom';
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
        <Link to="/" className="group min-w-0 shrink">
          <span className="block font-display text-base font-700 leading-none tracking-tight text-ink transition-colors group-hover:text-signal sm:text-lg">
            ENGINEERS&rsquo; DAY
            <span className="text-signal"> 2026</span>
          </span>
          <span className="label-tech mt-1 hidden sm:block">{SITE.org}</span>
        </Link>

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
      </div>
    </header>
  );
}
