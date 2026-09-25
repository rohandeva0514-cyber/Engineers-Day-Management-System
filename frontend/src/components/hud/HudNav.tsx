import { Link } from 'react-router-dom';
import { BrandLogo } from '@/components/BrandLogo';
import { SystemClock } from './SystemClock';
import '@/styles/hud-nav.css';

const LINKS = [
  { to: '/events', label: 'EVENTS' },
  { to: '/my-registrations', label: 'REGISTRY' },
] as const;

/**
 * The persistent navigation strip.
 *
 * Deliberately a plain list of links in normal document flow order, styled as a
 * status bar. The cinematic layer must never be able to trap someone: whatever
 * the boot sequence or a scroll timeline is doing, tabbing once from the top of
 * the page reaches a real route.
 *
 * It is hidden while the boot screen owns the viewport — not because it should
 * not exist then, but because the boot screen is itself a focus context with its
 * own single action, and two competing tab stops on a black screen is worse than
 * one.
 *
 * No bar, no border, no blur panel. Over a title sequence, chrome like that
 * announces "web page" before the title gets a chance to announce anything, so
 * the nav simply floats in the margin.
 */
export function HudNav() {
  return (
    <nav className="hud-nav" aria-label="Primary">
      {/* The logo replaces the MTK//ED26 text mark. Small, in the margin, and
          deliberately not in the centre: the hero's own title is the brand
          moment on this route and the logo must not compete with it. */}
      <Link to="/" className="hud-nav__mark" aria-label="Engineers&rsquo; Day 2026, home">
        <BrandLogo mark="institute" size="md" decorative />
      </Link>

      <ul className="hud-nav__links">
        {LINKS.map((link) => (
          <li key={link.to}>
            <Link to={link.to} className="hud-nav__link">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>

      {/* Right cluster: the clock, then the community mark. The clock is not a
          status light — the hero already reports SYS.STATUS at the bottom edge,
          and two live indicators on one screen is one too many. */}
      <div className="hud-nav__right">
        <p className="hud-nav__time" aria-hidden="true">
          <SystemClock />
        </p>
        <BrandLogo mark="kernel" size="md" decorative />
      </div>
    </nav>
  );
}
