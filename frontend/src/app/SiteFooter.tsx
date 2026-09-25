import { Link } from 'react-router-dom';
import { SITE } from '@/data/siteContent';

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-line bg-panel/30">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-8 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div>
          <p className="font-display text-sm text-ink">{SITE.org}</p>
          <p className="label-tech mt-1">{SITE.event}</p>
        </div>

        <nav aria-label="Footer">
          <ul className="flex flex-wrap gap-x-5 gap-y-2">
            <li>
              <Link to="/events" className="label-tech hover:text-ink transition-colors">
                Events
              </Link>
            </li>
            <li>
              <Link to="/my-registrations" className="label-tech hover:text-ink transition-colors">
                My Registrations
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </footer>
  );
}
