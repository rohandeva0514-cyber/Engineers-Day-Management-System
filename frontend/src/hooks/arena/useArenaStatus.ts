import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchArenaStatus } from '@/services/arena/arenaApi';
import type { ArenaStatusSnapshot } from '@/services/arena/arenaTypes';

/**
 * Polls the arena gate.
 *
 * The problem this solves: thirty students are sitting at terminals waiting, and
 * an organiser presses Start. Nobody should have to tell them all to refresh, and
 * nobody should be watching a closed door that opened two minutes ago.
 *
 * Polling rather than a WebSocket, for now. Thirty participants at one request
 * every ten seconds is three requests a second against a single primary-key
 * lookup, which is nothing, and it works through any proxy without a second
 * protocol to operate on event day. The shape of this hook is what a socket
 * transport would replace later — callers see a snapshot and a refresh, not a
 * transport.
 *
 * Three refinements that matter more than the interval:
 *
 * 1. **Polling stops while the tab is hidden.** A student who tabs away to read
 *    something should not keep a request loop running, and the browser throttles
 *    background timers unpredictably anyway.
 * 2. **Returning to the tab refetches immediately.** This is the case that
 *    actually happens — someone looks back at the screen and needs the truth now,
 *    not up to ten seconds later.
 * 3. **A failed poll keeps the last known snapshot.** A single dropped request on
 *    hall wifi must not blank a screen that was showing something correct; the
 *    error surfaces only when there is nothing to show at all.
 */

const POLL_INTERVAL_MS = 10_000;

export interface ArenaStatusState {
  snapshot: ArenaStatusSnapshot | null;
  /** True only until the first response — never during a background refresh. */
  loading: boolean;
  /** Set when a poll failed AND there is no earlier snapshot to keep showing. */
  unreachable: boolean;
  refresh: () => void;
}

export function useArenaStatus(): ArenaStatusState {
  const [snapshot, setSnapshot] = useState<ArenaStatusSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [unreachable, setUnreachable] = useState(false);

  // Read inside the poll without making it a dependency — re-creating the loop on
  // every snapshot change would reset the interval on every tick.
  const hasSnapshot = useRef(false);

  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const next = await fetchArenaStatus(signal);
      hasSnapshot.current = true;
      setSnapshot(next);
      setUnreachable(false);
    } catch {
      // Keep whatever was on screen. A transient failure is not news the student
      // can act on; a total inability to reach the service is.
      if (!hasSnapshot.current) setUnreachable(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let timer: number | undefined;

    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    };

    const start = () => {
      stop();
      timer = window.setInterval(() => void load(controller.signal), POLL_INTERVAL_MS);
    };

    const onVisibility = () => {
      if (document.hidden) {
        stop();
        return;
      }
      // Back on screen: answer now, then resume the cadence.
      void load(controller.signal);
      start();
    };

    // Synchronising with an external system — every state write here happens after
    // a request resolves, not during this render pass.
    // oxlint-disable-next-line react/set-state-in-effect
    void load(controller.signal);
    start();

    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('focus', onVisibility);

    return () => {
      stop();
      controller.abort();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('focus', onVisibility);
    };
  }, [load]);

  const refresh = useCallback(() => void load(), [load]);

  return { snapshot, loading, unreachable, refresh };
}
