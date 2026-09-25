import { useWaitPhase } from '@/hooks/useWaitPhase';

/**
 * The catalogue while it is still on its way.
 *
 * Two jobs. It holds the shape of the grid so the page does not jump when seven
 * events land, and it tells the truth about a wait that can legitimately run past a
 * minute when the event service has been idle and has to start before it can answer.
 *
 * <h2>Frames, not placeholder events</h2>
 *
 * Every bar here is blank. No invented names, no dummy seat counts, nothing that
 * could be read as real data and screenshotted as a real event. The frames carry
 * `EventCard`'s geometry — hairline border, header strip, accent rail, 2x2 meta grid
 * — so what arrives replaces them in place rather than reflowing the page.
 *
 * <h2>The wait says different things as it stretches</h2>
 *
 * A message that is identical at one second and at fifty reads as broken long before
 * it is. The readout advances once, at twelve seconds, from a plain load to an
 * explanation of why this particular wait is longer than a page load should be.
 * It describes the service, never the hosting underneath it: what a student needs to
 * know is that waiting will work, not where the thing runs.
 */

/** One advance, at twelve seconds — inside the 10–15s window where a wait starts to feel wrong. */
const PHASE_THRESHOLDS_MS = [12_000] as const;

const PHASES = [
  {
    readout: 'LOADING EVENT MANIFEST',
    detail: 'Fetching the event catalogue.',
  },
  {
    readout: 'CONNECTING TO EVENT SERVICE',
    detail:
      'The event service is starting up and will answer shortly. The first connection of the day can take up to two minutes — this page updates on its own, so there is no need to reload.',
  },
] as const;

const FRAME_COUNT = 6;

export function EventCatalogueSkeleton() {
  const phase = useWaitPhase(PHASE_THRESHOLDS_MS);
  const current = PHASES[Math.min(phase, PHASES.length - 1)] ?? PHASES[0];

  return (
    <div aria-busy="true">
      {/* The readout. `role="status"` announces the change of phase politely rather
          than interrupting, which is exactly the urgency this has. */}
      <div
        role="status"
        className="clip-notch-tr border border-line bg-panel surface-grid-fine px-4 py-4 sm:px-6 sm:py-5"
      >
        <p className="flex items-center gap-3 font-mono text-[10px] uppercase leading-relaxed tracking-[0.2em] text-tech sm:text-[11px] sm:tracking-[0.26em]">
          {/* Amber, not green: the same rule the cinematic standby state follows.
              Green means live, and nothing here is live yet. */}
          <span aria-hidden="true" className="size-[5px] shrink-0 animate-pulse bg-orange" />
          {current.readout}
        </p>

        <p className="mt-2.5 max-w-prose text-[13px] leading-relaxed text-muted sm:text-sm">
          {current.detail}
        </p>

        {/* Indeterminate progress: a band crossing a hairline track. No percentage,
            because there is no honest one to show. */}
        <div className="mt-4 h-px w-full overflow-hidden bg-line">
          <div aria-hidden="true" className="anim-scan-sweep h-px w-full bg-tech/20" />
        </div>
      </div>

      {/* Decorative throughout — the readout above is the accessible account of this
          state, so none of the frames need announcing. */}
      <ul
        aria-hidden="true"
        className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3"
      >
        {Array.from({ length: FRAME_COUNT }, (_, index) => (
          <li key={index}>
            <CardFrame index={index} />
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * One empty slot, built to `EventCard`'s measurements.
 *
 * The only motion is a single slow band crossing the frame, staggered per card so the
 * grid reads as being scanned in sequence rather than pulsing in unison. The bars
 * themselves are static: six flashing cards is a strobe, not an interface.
 */
function CardFrame({ index }: { index: number }) {
  return (
    <div className="relative flex h-full flex-col overflow-hidden border border-line bg-panel surface-scanlines">
      <span
        aria-hidden="true"
        className="anim-scan-sweep pointer-events-none absolute inset-0"
        style={{ animationDelay: `${index * 140}ms` }}
      />

      {/* Accent rail, in structure grey. The real card's rail carries the event's own
          colour, which is not knowable yet and must not be guessed. */}
      <span aria-hidden="true" className="absolute left-0 top-0 h-10 w-[3px] bg-line-bright" />

      <div className="flex items-start justify-between gap-3 border-b border-line px-5 py-3.5 pl-6">
        <Bar className="h-2.5 w-28" />
        <Bar className="h-4 w-16 shrink-0" />
      </div>

      <div className="flex flex-1 flex-col px-5 py-5 pl-6">
        <Bar className="h-6 w-2/5" />
        <Bar className="mt-3.5 h-2.5 w-full" />
        <Bar className="mt-2 h-2.5 w-11/12" />
        <Bar className="mt-2 h-2.5 w-3/5" />

        <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-line pt-4">
          {Array.from({ length: 4 }, (_, cell) => (
            <div key={cell}>
              <Bar className="h-2 w-16" />
              <Bar className="mt-1.5 h-3 w-24" />
            </div>
          ))}
        </dl>

        <Bar className="mt-5 h-2.5 w-32" />
      </div>
    </div>
  );
}

/** A blank field. Square, `bg-raised`, and deliberately still. */
function Bar({ className }: { className: string }) {
  return <div className={`bg-raised ${className}`} />;
}
