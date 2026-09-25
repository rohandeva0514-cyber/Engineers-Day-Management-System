/**
 * How long a wait has been going on, expressed as a phase rather than a clock.
 *
 * A loading state that says the same thing at 1 second and at 50 seconds reads as
 * broken long before it actually is. This advances through phases on a timetable so
 * the UI can change what it says — and only what it says — as a wait stretches out.
 *
 * Phases, not elapsed milliseconds, because a component that re-renders every second
 * to redraw a counter is a lot of renders to spend on a spinner, and a ticking number
 * invites the reader to watch it.
 *
 * The wait's lifetime is the caller's own: phases start at mount and are abandoned at
 * unmount. Callers already render a loading component only while loading, so there is
 * no second notion of "active" to keep in step with the first.
 */

import { useEffect, useState } from 'react';

/**
 * Returns the current phase: `0` before the first threshold has elapsed, `1` after
 * it, and so on.
 *
 * @param thresholdsMs ascending delays, in ms, at which each next phase begins
 */
export function useWaitPhase(thresholdsMs: readonly number[]): number {
  const [phase, setPhase] = useState(0);

  // Callers pass an array literal, which is a new object on every render. Depending on
  // the array itself would restart the timers each render and the phase would never
  // advance, so the effect depends on the VALUES, serialised — and reads them back out
  // of that same string. No ref, and the dependency list stays honest.
  const schedule = thresholdsMs.join(',');

  useEffect(() => {
    const delays = schedule === '' ? [] : schedule.split(',').map(Number);

    const timers = delays.map((delay, index) => setTimeout(() => setPhase(index + 1), delay));
    return () => {
      for (const timer of timers) clearTimeout(timer);
    };
  }, [schedule]);

  return phase;
}
