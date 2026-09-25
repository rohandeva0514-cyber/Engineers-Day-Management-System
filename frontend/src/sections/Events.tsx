import { SCENE_MEDIA, useScrollScene } from '@/animations/useScrollScene';
import { drawIn, setUndrawn } from '@/animations/drawSvg';
import { gsap } from '@/animations/gsap';
import { MissionBoard } from '@/features/missions/MissionBoard';
import { useEvents } from '@/hooks/useEvents';
import { SITE } from '@/data/siteContent';
import { LevelTransition } from './level/LevelTransition';
import '@/styles/events.css';
import '@/styles/missions.css';

/**
 * Level 01 — mission select.
 *
 * The catalogue comes from `GET /api/events` through the existing hook. Nothing
 * here keeps a local copy of the event list: eligibility, team sizes, capacity
 * and registration status are the server's to state, and a mirror maintained in
 * the frontend is a mirror that eventually disagrees.
 *
 * Which means this section has three real states, and all three are designed
 * rather than left as a spinner and a stack trace:
 *
 *   STANDBY   the manifest is loading — slot frames, sized for the real board.
 *   OFFLINE   the API is unreachable — says so plainly, and offers a retry.
 *   ACTIVE    the board.
 *
 * The transition and the board share ONE section and ONE heading, so the
 * cinematic reveal and the document outline cannot fall out of sync.
 */

/** Placeholder frames while the manifest loads, sized from the copy constant. */
const STANDBY_SLOTS = Array.from({ length: SITE.eventCount }, (_, i) => i);

export function Events() {
  const events = useEvents();

  const rootRef = useScrollScene<HTMLElement>(
    ({ media }) => {
      const build = (start: string) => {
        gsap.timeline({
          scrollTrigger: { trigger: '[data-manifest]', start, once: true },
          defaults: { ease: 'power3.out' },
        })
          .add(drawIn('[data-bay]', { duration: 1.1, stagger: 0.05 }))
          .fromTo(
            '[data-manifest-line]',
            { opacity: 0, y: 14 },
            { opacity: 1, y: 0, duration: 0.7, stagger: 0.08 },
            '<0.25',
          );
      };

      setUndrawn('[data-bay]');
      media.add(SCENE_MEDIA.desktop, () => build('top 80%'));
      media.add(SCENE_MEDIA.compact, () => build('top 90%'));
    },
    // Rebuilt when the manifest arrives: the elements the scene targets do not
    // exist during loading, and the section's height changes when they do.
    [events.status],
  );

  return (
    <section ref={rootRef} className="events" aria-labelledby="events-title">
      <LevelTransition />

      <div data-manifest className="events__manifest">
        <svg className="events__bay" viewBox="0 0 1000 560" preserveAspectRatio="none" aria-hidden="true">
          <path data-bay d="M 0 34 L 0 0 L 34 0" vectorEffect="non-scaling-stroke" />
          <path data-bay d="M 966 0 L 1000 0 L 1000 34" vectorEffect="non-scaling-stroke" />
          <path data-bay d="M 1000 526 L 1000 560 L 966 560" vectorEffect="non-scaling-stroke" />
          <path data-bay d="M 34 560 L 0 560 L 0 526" vectorEffect="non-scaling-stroke" />
        </svg>

        <div data-manifest-line className="events__bay-body">
          {events.status === 'success' && <MissionBoard events={events.data} />}

          {(events.status === 'loading' || events.status === 'idle') && (
            <div className="events__standby" aria-busy="true">
              <p className="events__status">
                <span className="events__status-dot" aria-hidden="true" />
                Loading mission manifest
              </p>
              <ul className="events__slots" aria-hidden="true">
                {STANDBY_SLOTS.map((slot) => (
                  <li key={slot} className="events__slot" />
                ))}
              </ul>
            </div>
          )}

          {events.status === 'error' && (
            <div className="events__offline" role="alert">
              <p className="events__status" data-offline="true">
                <span className="events__status-dot" data-offline="true" aria-hidden="true" />
                Mission manifest unavailable
              </p>
              <p className="events__offline-body">
                The event service is not responding, so the board cannot be shown.
                Nothing is wrong with your registration — this page only reads.
              </p>
              <button type="button" className="events__retry" onClick={events.reload}>
                <span aria-hidden="true">[</span> Retry <span aria-hidden="true">]</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
