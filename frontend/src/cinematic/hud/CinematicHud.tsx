import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { worldState } from '../world/worldState';
import { SITE } from '@/data/siteContent';

/**
 * The HUD.
 *
 * DOM, not WebGL. Text in a canvas would need a font atlas, would be blurry at
 * fractional DPR, would not be selectable or translatable, and would be invisible
 * to a screen reader. The distinction the whole layer follows: *diegetic* signage
 * inside the world is WebGL; anything the viewer is meant to read is DOM.
 *
 * It is also the guarantee that the cinematic sequence never traps anyone — the
 * route out to the practical site is a real link, present from the first frame.
 */

interface CinematicHudProps {
  phase: 'INTRO' | 'DRIVE';
  beat: string;
  audioState: 'LOCKED' | 'READY' | 'MUTED' | 'UNAVAILABLE';
  onToggleAudio: () => void;
  onSkip: () => void;
}

export function CinematicHud({ phase, beat, audioState, onToggleAudio, onSkip }: CinematicHudProps) {
  const speedRef = useRef<HTMLSpanElement>(null);
  const progressRef = useRef<HTMLSpanElement>(null);

  /**
   * Readouts are driven by an interval, not by the render loop.
   *
   * A speed number updating 60 times a second is unreadable anyway, and writing
   * React state at frame rate is the most reliable way to turn a 60 fps scene into
   * a 30 fps one. 10 Hz, straight to the DOM node, costs nothing.
   */
  useEffect(() => {
    const id = window.setInterval(() => {
      if (speedRef.current !== null) {
        speedRef.current.textContent = Math.round(worldState.speed * 78)
          .toString()
          .padStart(3, '0');
      }
      if (progressRef.current !== null) {
        progressRef.current.textContent = `${Math.round(worldState.u * 100)
          .toString()
          .padStart(2, '0')}%`;
      }
    }, 100);
    return () => window.clearInterval(id);
  }, []);

  return (
    <div className="pointer-events-none fixed inset-0 z-20 select-none">
      {/* Top rail — identity and the way out. Never hidden, at any phase. */}
      <div className="flex items-start justify-between gap-4 p-4 sm:p-6">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[0.3em] text-tech/80">
            {SITE.org}
          </p>
          <p className="mt-1 font-mono text-[10px] uppercase tracking-[0.22em] text-ink/45">
            Engineers&rsquo; Day // 2026
          </p>
        </div>

        <div className="pointer-events-auto flex items-center gap-2">
          {audioState !== 'UNAVAILABLE' && (
            <button
              type="button"
              onClick={onToggleAudio}
              className="border border-ink/15 bg-void/50 px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-ink/70 backdrop-blur-sm transition-colors hover:border-signal/60 hover:text-signal"
            >
              {audioState === 'LOCKED' ? '♪ Enable sound' : audioState === 'MUTED' ? '♪ Unmute' : '♪ Mute'}
            </button>
          )}

          <Link
            to="/events"
            onClick={onSkip}
            className="border border-ink/15 bg-void/50 px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-ink/70 backdrop-blur-sm transition-colors hover:border-signal/60 hover:text-signal"
          >
            Skip to events
          </Link>
        </div>
      </div>

      {/* Bottom rail — instrument cluster. Only once the car is actually moving. */}
      <div
        className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-4 p-4 transition-opacity duration-700 sm:p-6"
        style={{ opacity: phase === 'DRIVE' ? 1 : 0 }}
      >
        <div className="flex items-end gap-6">
          <div>
            <p className="font-mono text-[9px] uppercase tracking-[0.22em] text-ink/40">Velocity</p>
            <p className="font-display text-3xl leading-none text-signal sm:text-4xl">
              <span ref={speedRef} style={{ fontVariantNumeric: 'tabular-nums' }}>
                000
              </span>
              <span className="ml-1.5 font-mono text-[10px] tracking-widest text-ink/40">KM/H</span>
            </p>
          </div>

          <div className="hidden sm:block">
            <p className="font-mono text-[9px] uppercase tracking-[0.22em] text-ink/40">Route</p>
            <p className="font-mono text-sm text-tech" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <span ref={progressRef}>00%</span>
            </p>
          </div>
        </div>

        <p className="font-mono text-[9px] uppercase tracking-[0.22em] text-ink/35">
          Scroll to drive
        </p>
      </div>

      {/* System beat readout — the boot log, bottom left during the opening. */}
      <div
        className="absolute bottom-6 left-4 transition-opacity duration-500 sm:left-6"
        style={{ opacity: phase === 'INTRO' ? 1 : 0 }}
      >
        <p className="font-mono text-[10px] uppercase tracking-[0.24em] text-tech/70">
          <span className="mr-2 text-signal">▸</span>
          {beat}
        </p>
      </div>

      {/* Corner brackets. Cheap, and they frame the world as an instrument view. */}
      <Bracket className="left-3 top-3 border-l border-t sm:left-5 sm:top-5" />
      <Bracket className="right-3 top-3 border-r border-t sm:right-5 sm:top-5" />
      <Bracket className="bottom-3 left-3 border-b border-l sm:bottom-5 sm:left-5" />
      <Bracket className="bottom-3 right-3 border-b border-r sm:bottom-5 sm:right-5" />
    </div>
  );
}

function Bracket({ className }: { className: string }) {
  return <span aria-hidden="true" className={`absolute size-5 border-signal/25 ${className}`} />;
}
