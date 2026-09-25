import { useCallback, useEffect, useState } from 'react';
import {
  clearAdminCredentials,
  fetchDashboard,
  fetchParticipants,
  hasAdminCredentials,
  setAdminCredentials,
  setEventStatus,
  setSystemRegistration,
  type AdminDashboard,
  type AdminEventRow,
  type AdminParticipantRow,
  type ParticipantFilters,
} from './adminApi';
import { BrandLogo } from '@/components/BrandLogo';
import { BRANCHES, divisionsForYear } from '@/data/academics';
import '@/styles/admin.css';

/**
 * The operations panel.
 *
 * Built for one job: an organiser glancing at a laptop mid-event and knowing, in
 * a few seconds, what is open, what is full, and who is registered. Density over
 * spectacle — no cinematic layer, no scroll timeline, no animation beyond a
 * status dot.
 *
 * Every control here goes through the admin API, which Spring Security guards.
 * Nothing on this screen is trusted: hiding a button would not stop a direct call,
 * so the button is a convenience and the backend is the enforcement.
 */
export function AdminPage() {
  const [signedIn, setSignedIn] = useState(hasAdminCredentials);

  if (!signedIn) {
    return <AdminSignIn onSignedIn={() => setSignedIn(true)} />;
  }
  return (
    <AdminConsole
      onSignOut={() => {
        clearAdminCredentials();
        setSignedIn(false);
      }}
    />
  );
}

/**
 * Sign-in.
 *
 * Credentials are verified by making a real authenticated request rather than by
 * comparing anything locally — there is no client-side check to get wrong, and a
 * password that works here is one the server has already accepted.
 */
function AdminSignIn({ onSignedIn }: { onSignedIn: () => void }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setAdminCredentials(username, password);

    try {
      await fetchDashboard();
      onSignedIn();
    } catch (cause) {
      clearAdminCredentials();
      setError(cause instanceof Error ? cause.message : 'Sign-in failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin admin--centred">
      <form className="admin__signin" onSubmit={submit}>
        <div className="admin__signin-marks">
          <BrandLogo mark="institute" size="sm" />
          <BrandLogo mark="kernel" size="sm" />
        </div>
        <h1 className="admin__signin-title">Operations</h1>
        <p className="admin__signin-note">
          Engineers&rsquo; Day 2026 registration control. Authorised organisers only.
        </p>

        <label className="admin__field">
          <span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
        </label>

        <label className="admin__field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error !== null && (
          <p className="admin__error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="admin__button admin__button--primary" disabled={busy}>
          {busy ? 'Checking…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}

function AdminConsole({ onSignOut }: { onSignOut: () => void }) {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setDashboard(await fetchDashboard());
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load the dashboard.');
    }
  }, []);

  useEffect(() => {
    // Synchronising with an external system — the state writes happen after the
    // request resolves, not during this render pass.
    // oxlint-disable-next-line react/set-state-in-effect
    void load();
  }, [load]);

  async function toggleEvent(row: AdminEventRow) {
    setBusy(row.eventId);
    try {
      await setEventStatus(
        row.eventId,
        row.storedStatus === 'REGISTRATION_CLOSED' ? 'REGISTRATION_OPEN' : 'REGISTRATION_CLOSED',
      );
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'That change did not apply.');
    } finally {
      setBusy(null);
    }
  }

  async function toggleSystem(open: boolean) {
    setBusy('system');
    try {
      await setSystemRegistration(open);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'That change did not apply.');
    } finally {
      setBusy(null);
    }
  }

  if (dashboard === null) {
    return (
      <div className="admin admin--centred">
        <p className="admin__muted">{error ?? 'Loading…'}</p>
      </div>
    );
  }

  const { overview, events } = dashboard;

  return (
    <div className="admin">
      <header className="admin__bar">
        <div className="admin__brand">
          {/* Smaller here than anywhere else: the panel is an instrument, and the
              operator needs the tables, not the branding. */}
          <BrandLogo mark="institute" size="sm" decorative />
          <div>
            <p className="admin__eyebrow">Engineers&rsquo; Day 2026</p>
            <h1 className="admin__title">Registration control</h1>
          </div>
        </div>
        <div className="admin__bar-actions">
          <button type="button" className="admin__button" onClick={() => void load()}>
            Refresh
          </button>
          <button type="button" className="admin__button" onClick={onSignOut}>
            Sign out
          </button>
          <BrandLogo mark="kernel" size="sm" decorative className="ml-2 hidden sm:block" />
        </div>
      </header>

      {error !== null && (
        <p className="admin__error" role="alert">
          {error}
        </p>
      )}

      {/* The master switch leads: it overrides every per-event status below it,
          so an operator must see its state before reading anything else. */}
      <section className="admin__master" data-open={overview.registrationSystemOpen}>
        <div>
          <p className="admin__label">Registration system</p>
          <p className="admin__master-status">
            <span className="admin__dot" aria-hidden="true" />
            {overview.registrationSystemOpen ? 'OPEN' : 'CLOSED'}
          </p>
          <p className="admin__muted">
            {overview.registrationSystemOpen
              ? 'Entries are being accepted where the event is also open.'
              : 'All entries are refused, whatever an individual event says.'}
          </p>
        </div>
        <button
          type="button"
          className="admin__button admin__button--primary"
          disabled={busy === 'system'}
          onClick={() => void toggleSystem(!overview.registrationSystemOpen)}
        >
          {overview.registrationSystemOpen ? 'Stop all registrations' : 'Start registrations'}
        </button>
      </section>

      <section className="admin__stats">
        <Stat label="Participants" value={overview.totalParticipants} />
        <Stat label="Registrations" value={overview.totalRegistrations} />
        <Stat label="Open events" value={overview.openEvents} />
        <Stat label="Closed events" value={overview.closedEvents} />
      </section>

      <section aria-labelledby="events-heading">
        <h2 id="events-heading" className="admin__section-title">
          Events
        </h2>
        <div className="admin__table-wrap">
          <table className="admin__table">
            <thead>
              <tr>
                <th scope="col">Event</th>
                <th scope="col">Status</th>
                <th scope="col">Registrations</th>
                <th scope="col">Capacity</th>
                <th scope="col">Control</th>
              </tr>
            </thead>
            <tbody>
              {events.map((row) => (
                <EventRow
                  key={row.eventId}
                  row={row}
                  busy={busy === row.eventId}
                  onToggle={() => void toggleEvent(row)}
                />
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <ParticipantBrowser events={events} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="admin__stat">
      <p className="admin__label">{label}</p>
      <p className="admin__stat-value">{value}</p>
    </div>
  );
}

/**
 * One event row.
 *
 * Status is never colour alone — every state carries a word, because this gets
 * read quickly on a laptop in a bright hall.
 */
function EventRow({
  row,
  busy,
  onToggle,
}: {
  row: AdminEventRow;
  busy: boolean;
  onToggle: () => void;
}) {
  const full = row.status === 'SOLD_OUT';
  const manuallyClosed = row.storedStatus === 'REGISTRATION_CLOSED';

  const label = manuallyClosed ? 'MANUALLY CLOSED' : full ? 'FULL' : 'OPEN';
  const tone = manuallyClosed ? 'closed' : full ? 'full' : 'open';

  return (
    <tr>
      <th scope="row">
        <span className="admin__event-name">{row.name}</span>
        <span className="admin__muted admin__event-id">{row.eventId}</span>
      </th>

      <td>
        <span className="admin__badge" data-tone={tone}>
          <span className="admin__dot" aria-hidden="true" />
          {label}
        </span>
        {/* The distinction an operator most needs: did we close this, or did it
            close itself? */}
        {row.closedByCapacity && (
          <span className="admin__muted admin__note">Registrations closed automatically</span>
        )}
      </td>

      <td data-tabular>{row.registrations}</td>

      <td data-tabular>
        {row.capacity === null ? (
          <span className="admin__muted">No limit</span>
        ) : (
          <span className={full ? 'admin__full' : undefined}>
            {row.seatsTaken} / {row.capacity}
          </span>
        )}
      </td>

      <td>
        <button type="button" className="admin__button" disabled={busy} onClick={onToggle}>
          {manuallyClosed ? 'Open registrations' : 'Close registrations'}
        </button>
      </td>
    </tr>
  );
}

/** Event-wise participant list with server-side filtering. */
function ParticipantBrowser({ events }: { events: AdminEventRow[] }) {
  const [filters, setFilters] = useState<ParticipantFilters>({});
  const [rows, setRows] = useState<AdminParticipantRow[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    // Debounced: the search box filters server-side, and a request per keystroke
    // would queue behind itself on a slow connection.
    const timer = window.setTimeout(() => {
      fetchParticipants(filters)
        .then((page) => {
          if (cancelled) return;
          setRows(page.participants);
          setTotal(page.total);
          setError(null);
        })
        .catch((cause: unknown) => {
          if (!cancelled) {
            setError(cause instanceof Error ? cause.message : 'Could not load participants.');
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [filters]);

  const patch = (next: Partial<ParticipantFilters>) =>
    setFilters((current) => ({ ...current, ...next }));

  const yearValue = filters.year === undefined || filters.year === '' ? null : Number(filters.year);

  return (
    <section aria-labelledby="participants-heading">
      <h2 id="participants-heading" className="admin__section-title">
        Participants <span className="admin__muted" data-tabular>{total}</span>
      </h2>

      <div className="admin__filters">
        <label className="admin__field">
          <span>Search</span>
          <input
            value={filters.search ?? ''}
            onChange={(e) => patch({ search: e.target.value })}
            placeholder="Name, email or roll number"
          />
        </label>

        <label className="admin__field">
          <span>Event</span>
          <select value={filters.eventId ?? ''} onChange={(e) => patch({ eventId: e.target.value })}>
            <option value="">All events</option>
            {events.map((event) => (
              <option key={event.eventId} value={event.eventId}>
                {event.name}
              </option>
            ))}
          </select>
        </label>

        <label className="admin__field">
          <span>Year</span>
          <select value={filters.year ?? ''} onChange={(e) => patch({ year: e.target.value })}>
            <option value="">All years</option>
            <option value="1">First year</option>
            <option value="2">Second year</option>
          </select>
        </label>

        <label className="admin__field">
          <span>Branch</span>
          <select value={filters.branch ?? ''} onChange={(e) => patch({ branch: e.target.value })}>
            <option value="">All branches</option>
            {BRANCHES.map((branch) => (
              <option key={branch} value={branch}>
                {branch}
              </option>
            ))}
          </select>
        </label>

        <label className="admin__field">
          <span>Division</span>
          <select
            value={filters.division ?? ''}
            onChange={(e) => patch({ division: e.target.value })}
          >
            <option value="">All divisions</option>
            {divisionsForYear(yearValue === 1 || yearValue === 2 ? yearValue : null).map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error !== null && (
        <p className="admin__error" role="alert">
          {error}
        </p>
      )}

      <div className="admin__table-wrap">
        <table className="admin__table admin__table--dense">
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Name</th>
              <th scope="col">Email</th>
              <th scope="col">Roll</th>
              <th scope="col">Phone</th>
              <th scope="col">Branch</th>
              <th scope="col">Div</th>
              <th scope="col">Yr</th>
              <th scope="col">Event</th>
              <th scope="col">Team</th>
              <th scope="col">Access ID</th>
              <th scope="col">Registered</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.registrationId}>
                <td data-tabular>{index + 1}</td>
                <th scope="row">{row.fullName}</th>
                <td>{row.email}</td>
                <td data-tabular>{row.rollNo}</td>
                <td data-tabular>{row.phone ?? '—'}</td>
                <td>{row.branch ?? '—'}</td>
                <td>{row.division ?? '—'}</td>
                <td data-tabular>{row.yearLevel}</td>
                <td>{row.eventName}</td>
                <td>{row.teamName ?? '—'}</td>
                {/* Only events that issue one have a code; the rest show a dash
                    rather than an empty cell that reads as missing data. */}
                <td data-tabular className={row.accessCode === null ? undefined : 'admin__code'}>
                  {row.accessCode ?? '—'}
                </td>
                <td data-tabular>{new Date(row.registeredAt).toLocaleString()}</td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={12} className="admin__muted admin__empty">
                  No participants match these filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
