import { useLayoutEffect } from 'react';
import { drawIn, setUndrawn } from '@/animations/drawSvg';
import { gsap } from '@/animations/gsap';
import { scrambleTo } from '@/animations/scramble';
import { SCENE_MEDIA, useScrollScene } from '@/animations/useScrollScene';
import { SITE } from '@/data/siteContent';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { HeroEnvironment } from './hero/HeroEnvironment';
import '@/styles/environment.css';
import '@/styles/hero.css';

/**
 * The title screen.
 *
 * Type does the work. The title is large, tightly tracked and quiet; the
 * schematic behind it never rises above 9% opacity; green appears twice. What
 * makes it feel engineered is the restraint and the spacing, not the effects.
 *
 * The entrance is one orchestrated sequence rather than scattered reveals: the
 * sheet draws, the kernel line resolves out of noise, the title clips up into
 * frame with a single short settle, and the supporting lines follow. Then it
 * stops and stays stopped.
 *
 * Two independent timelines live here and must not collide:
 *
 *   ENTRANCE plays once and animates the CHILDREN — title lines, readouts,
 *   supporting copy.
 *
 *   EXIT is scrubbed by scroll and animates their CONTAINERS — the stage, the
 *   readout columns, the schematic.
 *
 * Keeping them on different elements is what lets someone scroll during the
 * entrance without the two writing to the same transform and fighting.
 */

/** Whole days until registration closes. Negative once the date has passed. */
function daysUntilDeadline(): number {
  const deadline = new Date(SITE.registrationDeadline).getTime();
  return Math.ceil((deadline - Date.now()) / 86_400_000);
}

interface HeroProps {
  /**
   * False while the boot screen owns the viewport.
   *
   * The hero is mounted underneath the whole time so its layout, fonts and
   * metrics are already resolved when the glitch lifts — but it must not *play*
   * until it is visible, or the entrance happens behind a black screen and the
   * visitor arrives at a finished, static page.
   */
  active: boolean;
}

export function Hero({ active }: HeroProps) {
  /**
   * The exit.
   *
   * Scrubbed across the hero's own height, so the section is being *left*
   * rather than scrolled past: the stage lifts and shrinks away from the
   * reader, the title leads the supporting copy out, the instruments drop, and
   * the schematic trails behind at a slower rate to open up depth.
   *
   * It runs on desktop only. On a phone this is a tall section on a short
   * screen and the same scrub would make the copy unreadable for most of its
   * travel; there, the hero simply scrolls.
   */
  const rootRef = useScrollScene<HTMLElement>(
    ({ root, media }) => {
      media.add(SCENE_MEDIA.desktop, () => {
        const exit = gsap.timeline({
          scrollTrigger: {
            trigger: root,
            start: 'top top',
            end: 'bottom top',
            scrub: 0.6,
          },
          defaults: { ease: 'none' },
        });

        exit
          .to('[data-hero-stage]', { yPercent: -14, scale: 0.94, opacity: 0 }, 0)
          // The title outruns the copy beneath it, so the block comes apart on
          // the way out instead of sliding as one plate.
          .to('[data-title]', { yPercent: -38 }, 0)
          .to('[data-reveal="statement"], [data-reveal="creed"]', { yPercent: -16 }, 0)
          .to('[data-hero-readouts]', { y: 44, opacity: 0, ease: 'power1.in' }, 0)
          // Slower than the content, and pushing in: the reader moves past the
          // schematic rather than the schematic moving with them.
          .to('.env', { yPercent: 9, scale: 1.07 }, 0)
          .to('.env-bloom', { opacity: 0.35 }, 0);
      });
    },
    [active],
  );

  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const root = rootRef.current;
    if (root === null) return;

    const context = gsap.context(() => {
      if (!active) {
        // Held at the pre-entrance state rather than hidden behind the parent:
        // when the boot screen lifts there is no flash of the settled
        // composition before the timeline takes over.
        setUndrawn('[data-draw]');
        gsap.set('[data-reveal]', { opacity: 0 });
        gsap.set('[data-title-line]', { opacity: 0, yPercent: 100 });
        return;
      }

      const tl = gsap.timeline({ defaults: { ease: 'expo.out' } });

      // 1. The sheet draws itself, slowly, underneath everything.
      tl.add(drawIn('[data-draw]', { duration: 2.6, stagger: 0.004, ease: 'power1.inOut' }))

        // 2. The kernel line resolves out of noise.
        .fromTo('[data-reveal="kernel"]', { opacity: 0 }, { opacity: 1, duration: 0.4 }, '<0.2');

      const kernel = root.querySelector<HTMLElement>('[data-scramble]');
      if (kernel !== null) {
        tl.add(scrambleTo(kernel, kernel.textContent ?? '', { duration: 0.9 }), '<');
      }

      // 3. The title rises into frame. Each line sits in its own overflow-hidden
      //    row, so this is a reveal from behind the line above rather than a
      //    slide across it.
      tl.fromTo(
        '[data-title-line]',
        { yPercent: 100, opacity: 0 },
        { yPercent: 0, opacity: 1, duration: 1.15, stagger: 0.12 },
        '-=0.35',
      )

        // 4. One brief settle as it lands — a short horizontal nudge and a
        //    flicker, over in under a fifth of a second. This is the only
        //    moment on the page that could be called a glitch.
        .to('[data-title]', { x: -6, duration: 0.05, ease: 'none' }, '-=0.45')
        .to('[data-title]', { x: 0, duration: 0.18, ease: 'expo.out' })
        .fromTo('[data-title-rule]', { scaleX: 0 }, { scaleX: 1, duration: 0.9 }, '-=0.5')

        // 5. Everything a person actually reads, then the instruments.
        .fromTo('[data-reveal="statement"]', { opacity: 0, y: 16 }, { opacity: 1, y: 0, duration: 0.85 }, '-=0.6')
        .fromTo('[data-reveal="creed"]', { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: 0.7 }, '-=0.6')
        .fromTo('[data-readout]', { opacity: 0 }, { opacity: 1, duration: 0.6, stagger: 0.07 }, '-=0.45');
    }, rootRef);

    return () => context.revert();
    // `rootRef` is a stable ref object from useScrollScene, but it is a hook
    // return rather than a useRef call, so it is declared explicitly.
  }, [active, rootRef]);

  const daysLeft = daysUntilDeadline();

  return (
    <section ref={rootRef} className="hero" aria-labelledby="hero-title">
      <div className="env-bloom" aria-hidden="true" />
      <HeroEnvironment />
      <div className="env-scan" aria-hidden="true" />

      <div data-hero-stage className="hero__stage">
        <p data-reveal="kernel" className="hero__kernel">
          <span data-scramble>MIT TECH KERNEL</span>
        </p>

        {/* Each line gets its own clipping row so the reveal comes from behind
            the one above it. */}
        <h1 data-title id="hero-title" className="hero__title">
          <span className="hero__title-row">
            <span data-title-line className="hero__title-name">
              Engineers&rsquo; Day
            </span>
          </span>
          <span className="hero__title-row">
            <span data-title-line className="hero__title-year">
              2026
            </span>
          </span>
        </h1>

        <div data-title-rule className="hero__rule" aria-hidden="true" />

        <p data-reveal="statement" className="hero__statement">
          Engineer the future.
        </p>

        <p data-reveal="creed" className="hero__creed">
          Build <span aria-hidden="true">·</span> Break <span aria-hidden="true">·</span> Solve{' '}
          <span aria-hidden="true">·</span> Repeat
        </p>
      </div>

      {/* Instruments sit on the bottom edge and stay out of the title's way. */}
      <dl data-hero-readouts className="hero__readouts hero__readouts--left">
        <div data-readout className="hero__readout">
          <dt>SYS.STATUS</dt>
          <dd className="hero__readout-live">
            <span className="hero__pulse" aria-hidden="true" />
            ONLINE
          </dd>
        </div>
        <div data-readout className="hero__readout">
          <dt>MISSIONS</dt>
          <dd data-tabular>{String(SITE.eventCount).padStart(2, '0')}</dd>
        </div>
      </dl>

      <dl data-hero-readouts className="hero__readouts hero__readouts--right">
        <div data-readout className="hero__readout">
          <dt>REG.CLOSES</dt>
          <dd data-tabular>{daysLeft > 0 ? `${String(daysLeft).padStart(2, '0')}D` : 'CLOSED'}</dd>
        </div>
        <div data-readout className="hero__readout">
          <dt>SCROLL</dt>
          <dd aria-hidden="true">▼</dd>
        </div>
      </dl>
    </section>
  );
}
