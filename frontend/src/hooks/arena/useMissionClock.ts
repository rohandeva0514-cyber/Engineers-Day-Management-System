import { useEffect, useMemo, useRef, useState } from 'react';

/**
 * A countdown anchored to the server's clock, not the browser's.
 *
 * The problem this solves is real and common: lab machines have wrong clocks.
 * A browser five minutes fast would show a mission deadline that has already
 * passed; one five minutes slow would show time remaining after the backend had
 * stopped accepting anything. Neither is acceptable on a screen a student is
 * pacing themselves against.
 *
 * So every arena response carries `serverTime`. The moment one arrives we compute
 *
 *     offset = serverTime − (the browser's clock, right now)
 *
 * and render `expiresAt − (Date.now() + offset)` from then on. Local *elapsed* time
 * is reliable even when the absolute clock is not, so one anchor per response is
 * enough and the display stays smooth between them.
 *
 * None of this is authority. The client timer is decoration: if it drifts, is
 * tampered with in devtools, or is simply wrong, the next request is refused with
 * ATTEMPT_EXPIRED and the UI switches to the expired screen. This exists so a
 * student can pace themselves, not so the deadline can be enforced.
 */

export interface MissionClock {
  /** Whole seconds left, floored at zero. */
  remainingSeconds: number;
  /** `MM:SS`, zero-padded, ready to render. */
  display: string;
  /** Drives the escalating treatment. Never the only signal — the digits are. */
  urgency: 'calm' | 'aware' | 'urgent' | 'critical';
  expired: boolean;
}

export function useMissionClock(
  expiresAt: string | null,
  serverTime: string | null,
): MissionClock {
  const deadlineMs = useMemo(() => {
    if (expiresAt === null) return null;
    const parsed = Date.parse(expiresAt);
    return Number.isNaN(parsed) ? null : parsed;
  }, [expiresAt]);

  // The offset is re-anchored in an effect, never during render. Reading the wall
  // clock while rendering is impure — two renders of the same props would disagree —
  // and a ref is the right home for a value the ticker reads but nothing displays.
  const offsetMs = useRef(0);
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    if (serverTime !== null) {
      const server = Date.parse(serverTime);
      if (!Number.isNaN(server)) offsetMs.current = server - Date.now();
    }

    if (deadlineMs === null) return;

    // Synchronising with an external system — the wall clock. The first value has
    // to land immediately or the countdown shows a placeholder for one second.
    // oxlint-disable-next-line react/set-state-in-effect
    setRemainingSeconds(secondsLeft(deadlineMs, offsetMs.current));

    const timer = window.setInterval(
      () => setRemainingSeconds(secondsLeft(deadlineMs, offsetMs.current)),
      1_000,
    );
    return () => window.clearInterval(timer);
  }, [deadlineMs, serverTime]);

  return {
    remainingSeconds,
    display: format(remainingSeconds),
    urgency: urgencyOf(remainingSeconds, deadlineMs !== null),
    expired: deadlineMs !== null && remainingSeconds <= 0,
  };
}

function secondsLeft(deadlineMs: number | null, offsetMs: number): number {
  if (deadlineMs === null) return 0;
  return Math.max(0, Math.floor((deadlineMs - (Date.now() + offsetMs)) / 1000));
}

/** `MM:SS`. Minutes are not capped at 59 — a 90-minute event should read `90:00`. */
function format(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/**
 * Thresholds, matching the animation spec.
 *
 * Restraint is the point. The clock's treatment escalates; nothing else on the
 * screen does. No full-screen effects, no sound, no modal — a student mid-thought
 * at 04:59 should notice the colour change and not lose the thought.
 */
function urgencyOf(remainingSeconds: number, running: boolean): MissionClock['urgency'] {
  if (!running) return 'calm';
  if (remainingSeconds <= 60) return 'critical';
  if (remainingSeconds <= 300) return 'urgent';
  if (remainingSeconds <= 600) return 'aware';
  return 'calm';
}
