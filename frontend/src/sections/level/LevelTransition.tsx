import { SCENE_MEDIA, useScrollScene } from '@/animations/useScrollScene';
import { gsap } from '@/animations/gsap';
import '@/styles/level.css';

/**
 * The stage change between the title screen and the events.
 *
 * Pinned on desktop and scrubbed across roughly two viewport heights, so the
 * reader is not watching an animation play — they are driving it. Five beats,
 * in order: the frame draws, a scan crosses, the level index arrives, a meter
 * fills to 100, and the section name resolves as the index recedes.
 *
 * It carries the real `<h2>` for the events section rather than a decorative
 * copy of it. That is deliberate: the cinematic reveal and the document
 * heading are the same element, so there is exactly one "Events" in the
 * accessibility tree and no duplicated text to fall out of sync.
 *
 * On compact screens the pin is dropped entirely. A 200vh pinned section on a
 * short viewport means a long stretch of scrolling where nothing is readable;
 * there the same content plays as one short reveal as it enters frame.
 */
export function LevelTransition() {
  const rootRef = useScrollScene<HTMLDivElement>(({ root, media }) => {
    /** Writes the meter's readout without going through React state. */
    const countTo = (target: HTMLElement | null) => {
      const value = { n: 0 };
      return gsap.to(value, {
        n: 100,
        ease: 'none',
        // Stepped, so it reads as work completing rather than as a number
        // being interpolated.
        snap: { n: 1 },
        onUpdate: () => {
          if (target !== null) target.textContent = String(Math.round(value.n)).padStart(3, '0');
        },
      });
    };

    media.add(SCENE_MEDIA.desktop, () => {
      const readout = root.querySelector<HTMLElement>('[data-level-count]');

      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: root,
          start: 'top top',
          end: '+=200%',
          pin: true,
          // Short catch-up rather than 0: the beats land crisply but a fast
          // flick still reads as motion instead of teleporting.
          scrub: 0.5,
          anticipatePin: 1,
        },
        defaults: { ease: 'none' },
      });

      tl
        // 1. The frame arrives.
        .fromTo('[data-level-rule]', { scaleX: 0 }, { scaleX: 1, duration: 0.6 }, 0)
        .fromTo('[data-level-signal]', { opacity: 0 }, { opacity: 1, duration: 0.4 }, 0.1)

        // 2. A scan crosses the empty frame.
        .fromTo(
          '[data-level-scan]',
          { yPercent: -120, opacity: 0 },
          { yPercent: 0, opacity: 1, duration: 0.5 },
          0.35,
        )
        .to('[data-level-scan]', { yPercent: 120, opacity: 0, duration: 0.6 }, 0.85)

        // 3. The level index rises out of its own clip.
        .fromTo(
          '[data-level-index]',
          { yPercent: 105 },
          { yPercent: 0, duration: 0.8, ease: 'power2.out' },
          0.7,
        )

        // 4. The meter fills.
        .fromTo('[data-level-meter]', { scaleX: 0 }, { scaleX: 1, duration: 1 }, 1.15)
        .add(countTo(readout), 1.15)

        // 5. The name resolves as the index gives up the centre.
        .to('[data-level-index]', { yPercent: -18, opacity: 0.25, duration: 0.7 }, 2.3)
        .fromTo(
          '[data-level-name]',
          { yPercent: 100, opacity: 0 },
          { yPercent: 0, opacity: 1, duration: 0.8, ease: 'power2.out' },
          2.4,
        )
        .fromTo('[data-level-tag]', { opacity: 0 }, { opacity: 1, duration: 0.5 }, 2.85);
    });

    media.add(SCENE_MEDIA.compact, () => {
      const readout = root.querySelector<HTMLElement>('[data-level-count]');

      // No pin, no scrub: one reveal triggered once, as the block enters.
      const tl = gsap.timeline({
        scrollTrigger: { trigger: root, start: 'top 75%', once: true },
        defaults: { ease: 'power3.out' },
      });

      tl.fromTo('[data-level-rule]', { scaleX: 0 }, { scaleX: 1, duration: 0.7 })
        .fromTo('[data-level-signal]', { opacity: 0 }, { opacity: 1, duration: 0.4 }, '<0.1')
        .fromTo('[data-level-index]', { yPercent: 105 }, { yPercent: 0, duration: 0.7 }, '<')
        .fromTo('[data-level-meter]', { scaleX: 0 }, { scaleX: 1, duration: 0.9 }, '<0.2')
        .add(countTo(readout).duration(0.9), '<')
        .fromTo(
          '[data-level-name]',
          { yPercent: 100, opacity: 0 },
          { yPercent: 0, opacity: 1, duration: 0.7 },
          '<0.25',
        )
        .fromTo('[data-level-tag]', { opacity: 0 }, { opacity: 1, duration: 0.5 }, '<0.3');
    });
  });

  return (
    <div ref={rootRef} className="level">
      <div data-level-scan className="level__scan" aria-hidden="true" />

      <div className="level__inner">
        <p data-level-signal className="level__signal">
          <span className="level__signal-dot" aria-hidden="true" />
          System transition
        </p>

        <div data-level-rule className="level__rule" aria-hidden="true" />

        <div className="level__clip" aria-hidden="true">
          <p data-level-index className="level__index">
            Level 01
          </p>
        </div>

        <div className="level__meter-track" aria-hidden="true">
          <div data-level-meter className="level__meter" />
        </div>

        <p className="level__count" aria-hidden="true">
          <span data-level-count data-tabular>
            000
          </span>
          <span className="level__count-unit">%</span>
        </p>

        <div className="level__clip">
          <h2 data-level-name id="events-title" className="level__name">
            Events
          </h2>
        </div>

        <p data-level-tag className="level__tag">
          Mission select
        </p>
      </div>
    </div>
  );
}
