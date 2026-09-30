import { useCallback, useEffect, useState } from 'react';
import {
  fetchArenaControl,
  setArenaStatus,
  type ArenaControlView,
  type ArenaStatus,
} from './adminApi';

/**
 * Debugging Arena control.
 *
 * Its own component with its own load, rather than another branch of the
 * dashboard: the arena has a separate lifecycle from registration, it refreshes
 * on a different cadence, and folding it into `AdminConsole` would grow a
 * component that is already doing enough.
 *
 * Everything here is a convenience over the backend's state machine. Hiding a
 * button does not stop a direct call — `ArenaStatus.canTransitionTo` does.
 */
export function ArenaControlPanel() {
  const [control, setControl] = useState<ArenaControlView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setControl((await fetchArenaControl()).control);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load the arena.');
    }
  }, []);

  useEffect(() => {
    // Synchronising with an external system — the state write happens after the
    // request resolves, not during this render pass.
    // oxlint-disable-next-line react/set-state-in-effect
    void load();
  }, [load]);

  async function move(status: ArenaStatus) {
    // ENDED is the one control that finalizes other people's work, so it is the
    // one that asks. Every other transition is recoverable in a click.
    if (status === 'ENDED') {
      const confirmed = window.confirm(
        'End the arena?\n\n'
          + 'Every attempt still running will be finalized from its last saved code. '
          + 'This cannot be undone, and the arena cannot be restarted in one step.',
      );
      if (!confirmed) return;
    }

    setBusy(true);
    try {
      setControl(await setArenaStatus(status));
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'That change did not apply.');
    } finally {
      setBusy(false);
    }
  }

  if (control === null) {
    return (
      <section aria-labelledby="arena-heading">
        <h2 id="arena-heading" className="admin__section-title">
          Debugging Arena
        </h2>
        <p className="admin__muted">{error ?? 'Loading…'}</p>
      </section>
    );
  }

  const live = control.status === 'ACTIVE';

  return (
    <section aria-labelledby="arena-heading">
      <h2 id="arena-heading" className="admin__section-title">
        Debugging Arena
      </h2>

      {error !== null && (
        <p className="admin__error" role="alert">
          {error}
        </p>
      )}

      {/* Reuses the master-switch treatment: this is the other control in the
          panel that overrides everything beneath it, and it should read the same
          way at a glance. */}
      <section className="admin__master" data-open={live}>
        <div>
          <p className="admin__label">Arena status</p>
          <p className="admin__master-status">
            <span className="admin__dot" aria-hidden="true" />
            {control.status}
          </p>
          <p className="admin__muted">{describe(control)}</p>
          <p className="admin__muted">
            Mission length {Math.round(control.durationSeconds / 60)} minutes
            {control.updatedBy !== null && ` · last changed by ${control.updatedBy}`}
          </p>
        </div>

        <div className="admin__bar-actions">
          {control.status === 'ACTIVE' ? (
            <button
              type="button"
              className="admin__button"
              disabled={busy}
              onClick={() => void move('OFFLINE')}
            >
              Pause arena
            </button>
          ) : (
            <button
              type="button"
              className="admin__button admin__button--primary"
              disabled={busy || control.status === 'ENDED'}
              onClick={() => void move('ACTIVE')}
            >
              Start arena
            </button>
          )}

          {control.status === 'ENDED' ? (
            <button
              type="button"
              className="admin__button"
              disabled={busy}
              onClick={() => void move('OFFLINE')}
            >
              Reopen to offline
            </button>
          ) : (
            <button
              type="button"
              className="admin__button"
              disabled={busy}
              onClick={() => void move('ENDED')}
            >
              End &amp; finalize
            </button>
          )}
        </div>
      </section>
    </section>
  );
}

/** One sentence saying what the current status means for a participant. */
function describe(control: ArenaControlView): string {
  switch (control.status) {
    case 'ACTIVE':
      return 'Participants can check in and start their mission.';
    case 'ENDED':
      return 'The event is over. Submissions are closed and attempts have been finalized.';
    case 'OFFLINE':
    default:
      return control.openedAt === null
        ? 'Not started. Participants see a standby screen.'
        : 'Paused. Nobody can check in; attempts already running are frozen, not lost.';
  }
}
