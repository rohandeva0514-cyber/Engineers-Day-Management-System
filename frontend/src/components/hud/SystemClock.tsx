import { useEffect, useState } from 'react';

/** 24-hour local time, zero-padded, stable width. */
function stamp(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * A ticking system clock.
 *
 * Isolated into its own component on purpose: it re-renders once a second, and
 * anything rendered alongside it would re-render with it. Keeping it a leaf
 * means the hero's timeline, title and HUD are never touched by the tick.
 *
 * The interval is cleared on unmount, and nothing else in the tree depends on
 * the value — it is pure atmosphere, and it is honest atmosphere: the real local
 * time of the person looking at the screen.
 */
export function SystemClock() {
  const [now, setNow] = useState(() => stamp(new Date()));

  useEffect(() => {
    const id = window.setInterval(() => setNow(stamp(new Date())), 1000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <time data-tabular>{now}</time>
  );
}
