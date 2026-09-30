import type { ReactNode } from 'react';

/**
 * The arena's viewport shell.
 *
 * Everything the arena renders sits inside one of these: a full-height ground,
 * the static backdrop textures, and a single centred panel. Keeping the frame in
 * one component means every screen of the flow — gate, access, identity,
 * briefing, complete — shares one geometry, so moving between them reads as one
 * environment changing rather than as separate pages.
 *
 * `tone` drives the corner ticks and nothing else. Colour is never the only
 * signal on any arena screen; every state also says a word.
 */
export type ArenaTone = 'offline' | 'active' | 'ended' | 'error';

export function ArenaFrame({
  tone,
  children,
}: {
  tone: ArenaTone;
  children: ReactNode;
}) {
  return (
    <div className="arena">
      <div className="arena__backdrop" aria-hidden="true" />
      <section className="arena__panel" data-tone={tone}>
        {children}
      </section>
    </div>
  );
}

/**
 * The status line above the headline.
 *
 * Reads as an instrument: a small square that breathes, and a word. The word is
 * what carries the meaning — the dot is confirmation, never the message, because
 * this gets read at a glance in a bright hall and by people who cannot
 * distinguish the colours.
 */
export function ArenaStatusLine({ tone, label }: { tone: ArenaTone; label: string }) {
  return (
    <p className="arena__status" data-tone={tone}>
      <span className="arena__dot" aria-hidden="true" />
      {label}
    </p>
  );
}

/** One `KEY   value` row of the system log strip. */
export function ArenaLogLine({ label, value }: { label: string; value: string }) {
  return (
    <p className="arena__log-line">
      <span>{label}</span>
      <span className="arena__log-value">{value}</span>
    </p>
  );
}
