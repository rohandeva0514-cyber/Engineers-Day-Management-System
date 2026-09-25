import { Link } from 'react-router-dom';
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
 */
export function HudNav() {
  return (
    <nav className="hud-nav" aria-label="Primary">
      <Link to="/" className="hud-nav__mark">
        MTK<span aria-hidden="true">//</span>ED26
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

      <p className="hud-nav__status">
        <span className="hud-nav__dot" aria-hidden="true" />
        ONLINE
      </p>
    </nav>
  );
}
