import { useLayoutEffect, useRef, useState } from 'react';
import { gsap } from '@/animations/gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { isFeatured, missionBoard, missionSpan } from '@/domain/missions';
import type { Event, YearLevel } from '@/domain/types';
import { MissionCard } from './MissionCard';
import { RegistrationProtocol } from './RegistrationProtocol';
import { YearSelector } from './YearSelector';

interface MissionBoardProps {
  events: readonly Event[];
}

/**
 * The mission select.
 *
 * Owns one piece of state and nothing else: which year is showing. The board is
 * derived — `missionBoard()` is a pure function of (events, year), so switching
 * year cannot drift out of sync with what is rendered.
 *
 * Switching year replays a short activation on the new cards rather than
 * cross-fading. A swap this fast does not need a transition; what it needs is
 * to look like the board reloaded, which is what a scan plus a staggered clip
 * gives. It runs on mount too, so the first board arrives the same way.
 */
export function MissionBoard({ events }: MissionBoardProps) {
  const [year, setYear] = useState<YearLevel>(1);
  const gridRef = useRef<HTMLDivElement>(null);

  const tracks = missionBoard(events, year);
  const total = tracks.reduce((sum, track) => sum + track.missions.length, 0);

  // Keyed on `year`, so this is the activation for whichever board is current.
  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const context = gsap.context(() => {
      const tl = gsap.timeline({ defaults: { ease: 'power3.out' } });

      tl.fromTo('[data-board-scan]', { scaleX: 0, opacity: 1 }, { scaleX: 1, duration: 0.28, ease: 'power2.in' })
        .to('[data-board-scan]', { opacity: 0, duration: 0.2 })
        .fromTo(
          '[data-track-rule]',
          { scaleX: 0 },
          { scaleX: 1, duration: 0.5, stagger: 0.08 },
          '-=0.3',
        )
        .fromTo(
          '[data-mission]',
          // Clipped from the top with a small lift: the panels are cut in, not
          // floated up. Generic fade-up is what this is deliberately avoiding.
          { clipPath: 'inset(0 0 100% 0)', y: 18, opacity: 0 },
          {
            clipPath: 'inset(0 0 0% 0)',
            y: 0,
            opacity: 1,
            duration: 0.55,
            stagger: 0.055,
          },
          '-=0.35',
        );
    }, gridRef);

    return () => context.revert();
  }, [year]);

  return (
    <div className="board">
      <YearSelector value={year} onChange={setYear} count={total} />

      <RegistrationProtocol />

      <div ref={gridRef} className="board__grid">
        <span data-board-scan className="board__scan" aria-hidden="true" />

        {tracks.map((track) => (
          <section key={track.id} className="board__track" aria-labelledby={`track-${track.id}`}>
            <header className="board__track-head">
              <h3 id={`track-${track.id}`} className="board__track-name">
                {track.label}
              </h3>
              <p className="board__track-note">{track.note}</p>
              <span data-track-rule className="board__track-rule" aria-hidden="true" />
              <span className="board__track-count" data-tabular aria-hidden="true">
                {String(track.missions.length).padStart(2, '0')}
              </span>
            </header>

            <div className="board__missions">
              {track.missions.map((mission, i) => (
                <MissionCard
                  key={mission.event.eventId}
                  mission={mission}
                  featured={isFeatured(i)}
                  span={missionSpan(i, track.missions.length)}
                />
              ))}

              {/* A single-mission track leaves half the row empty. Annotating
                  that space is what makes it read as set apart rather than as a
                  layout that failed to fill. */}
              {track.missions.length === 1 && (
                <p className="board__aside" aria-hidden="true">
                  <span>Restricted deployment</span>
                  <span className="board__aside-rule" />
                  <span>Clearance {track.id === 'EXCLUSIVE' ? 'Y2' : 'Y1'}</span>
                </p>
              )}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
