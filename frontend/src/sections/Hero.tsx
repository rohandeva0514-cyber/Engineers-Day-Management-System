import { useCallback, useLayoutEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { drawIn, setUndrawn } from '@/animations/drawSvg';
import { gsap } from '@/animations/gsap';
import { scrambleTo } from '@/animations/scramble';
import { CornerBrackets } from '@/components/hud/CornerBrackets';
import { SystemClock } from '@/components/hud/SystemClock';
import { SITE } from '@/data/siteContent';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { HeroSchematic } from './HeroSchematic';
import '@/styles/hero.css';

/**
 * The title screen.
 *
 * Typography is the composition; everything else is frame. The schematic sits at
 * 8% opacity, the brackets are hairlines, and the HUD is 10px monospace — none
 * of it competes with the two words that matter.
 *
 * The entrance is an assembly, not a fade: the frame draws itself, then the
 * readouts resolve out of noise, then the title takes its place. Order matters
 * more than duration here — an interface that builds in the wrong order reads as
 * a slideshow no matter how it is eased.
 */

const CTA_LABEL = 'EXPLORE EVENTS';

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
  const rootRef = useRef<HTMLElement>(null);
  const ctaLabelRef = useRef<HTMLSpanElement>(null);

  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const root = rootRef.current;
    if (root === null) return;

    const context = gsap.context(() => {
      if (!active) {
        // Held at the pre-entrance state rather than hidden behind the parent:
        // when the boot screen lifts there is no flash of the settled
        // composition before the timeline takes over.
        setUndrawn('[data-bracket], [data-draw]');
        gsap.set('[data-reveal], [data-title-line], [data-word]', { opacity: 0 });
        gsap.set('[data-rule]', { scaleX: 0 });
        return;
      }

      const tl = gsap.timeline({ defaults: { ease: 'expo.out' } });

      // 1. The frame arrives first. Nothing is inside it yet.
      tl.add(drawIn('[data-bracket]', { duration: 0.5, stagger: 0.05 }))
        .fromTo('[data-rule]', { scaleX: 0 }, { scaleX: 1, duration: 1, stagger: 0.1 }, '<0.1')

        // 2. Readouts resolve out of noise — the machine reporting before it
        //    announces.
        .fromTo('[data-reveal="eyebrow"]', { opacity: 0 }, { opacity: 1, duration: 0.4 }, '<0.2');

      const eyebrow = root.querySelector<HTMLElement>('[data-scramble]');
      if (eyebrow !== null) {
        tl.add(scrambleTo(eyebrow, eyebrow.textContent ?? '', { duration: 0.8 }), '<');
      }

      // 3. The title takes the screen. Clip from below, so it rises into frame
      //    rather than appearing in it.
      tl.fromTo(
        '[data-title-line]',
        { opacity: 0, yPercent: 40, clipPath: 'inset(0 0 100% 0)' },
        {
          opacity: 1,
          yPercent: 0,
          clipPath: 'inset(0 0 0% 0)',
          duration: 1.1,
          stagger: 0.11,
        },
        '-=0.35',
      )
        // A single hard offset frame as it lands. One frame, not a loop — a
        // permanently glitching title is a screensaver.
        .set('[data-title]', { className: '+=is-hit' }, '-=0.5')
        .set('[data-title]', { className: '-=is-hit' }, '+=0.08')

        // 4. Statement, word by word.
        .fromTo(
          '[data-word]',
          { opacity: 0, yPercent: 60 },
          { opacity: 1, yPercent: 0, duration: 0.7, stagger: 0.06 },
          '-=0.75',
        )
        .fromTo('[data-reveal="sub"]', { opacity: 0, y: 10 }, { opacity: 1, y: 0, duration: 0.6 }, '-=0.4')
        .fromTo('[data-reveal="cta"]', { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.7 }, '-=0.4')

        // 5. The instrumentation comes up last — it is context, not headline.
        .fromTo(
          '[data-hud-cell]',
          { opacity: 0, y: 10 },
          { opacity: 1, y: 0, duration: 0.6, stagger: 0.07 },
          '-=0.5',
        )
        .fromTo('[data-reveal="rail"]', { opacity: 0 }, { opacity: 1, duration: 0.8 }, '-=0.5')
        .add(drawIn('[data-draw]', { duration: 1.6, stagger: 0.012, ease: 'power1.inOut' }), '-=1.6')
        .fromTo('[data-reveal="hint"]', { opacity: 0 }, { opacity: 1, duration: 0.6 }, '-=0.3');
    }, rootRef);

    return () => context.revert();
  }, [active]);

  // The CTA re-scrambles its own label on hover. Cheap, and it makes the button
  // feel like part of the same machine as the boot console.
  const scrambleCta = useCallback(() => {
    if (prefersReducedMotion() || ctaLabelRef.current === null) return;
    scrambleTo(ctaLabelRef.current, CTA_LABEL, { duration: 0.45 });
  }, []);

  const daysLeft = daysUntilDeadline();

  return (
    <section ref={rootRef} className="hero" aria-labelledby="hero-title">
      <CornerBrackets />
      <HeroSchematic />

      <div data-rule className="hero__rule hero__rule--top" aria-hidden="true" />

      <div className="hero__inner">
        <p data-reveal="eyebrow" className="hero__eyebrow">
          <span className="hero__eyebrow-dot" aria-hidden="true" />
          <span data-scramble>MIT TECH KERNEL // SYSTEM 01</span>
        </p>

        <h1 data-title id="hero-title" className="hero__title">
          <span data-title-line className="hero__title-line" data-text="Engineers’ Day">
            Engineers&rsquo; Day
          </span>
          <span data-title-line className="hero__title-line hero__title-line--year" data-text="2026">
            2026
          </span>
        </h1>

        <p className="hero__statement">
          {/* Split here rather than in CSS: each word needs to be its own
              transform target, and a word-splitting utility for two lines of
              copy is more machinery than the effect is worth. */}
          <span className="hero__word-row">
            <span data-word className="hero__word">
              Engineer
            </span>
          </span>
          <span className="hero__word-row">
            <span data-word className="hero__word">
              the
            </span>{' '}
            <span data-word className="hero__word">
              future.
            </span>
          </span>
        </p>

        <p data-reveal="sub" className="hero__sub">
          Build <span aria-hidden="true">·</span> Break <span aria-hidden="true">·</span> Solve{' '}
          <span aria-hidden="true">·</span> Repeat
        </p>

        <div data-reveal="cta" className="hero__actions">
          <Link to="/events" className="hero__cta" onMouseEnter={scrambleCta} onFocus={scrambleCta}>
            <span aria-hidden="true">[</span>
            <span ref={ctaLabelRef} className="hero__cta-label">
              {CTA_LABEL}
            </span>
            <span aria-hidden="true">&rarr;]</span>
          </Link>
        </div>

        <dl className="hero__hud">
          <div data-hud-cell className="hero__hud-cell">
            <dt className="label-tech">SYSTEM STATUS</dt>
            <dd className="hero__hud-value" data-kind="live">
              <span className="hero__hud-pulse" aria-hidden="true" />
              ONLINE
            </dd>
          </div>

          <div data-hud-cell className="hero__hud-cell">
            <dt className="label-tech">MISSIONS</dt>
            <dd className="hero__hud-value" data-tabular>
              {String(SITE.eventCount).padStart(2, '0')}
            </dd>
          </div>

          <div data-hud-cell className="hero__hud-cell">
            <dt className="label-tech">ENGINEER XP</dt>
            <dd className="hero__hud-value" data-tabular>
              0000
            </dd>
          </div>

          <div data-hud-cell className="hero__hud-cell">
            <dt className="label-tech">REGISTRATION</dt>
            <dd className="hero__hud-value" data-kind={daysLeft > 0 ? 'warn' : 'plain'} data-tabular>
              {daysLeft > 0 ? `CLOSES IN ${daysLeft}D` : 'CLOSED'}
            </dd>
          </div>
        </dl>
      </div>

      {/* Vertical metadata rail. Atmosphere, and the first thing dropped on a
          narrow screen — it is the only element here carrying no information the
          rest of the page does not already give. */}
      <aside data-reveal="rail" className="hero__rail" aria-hidden="true">
        <span>LAT 19.0760&deg;N</span>
        <span className="hero__rail-sep" />
        <span>LON 72.8777&deg;E</span>
        <span className="hero__rail-sep" />
        <SystemClock />
      </aside>

      <div data-rule className="hero__rule hero__rule--bottom" aria-hidden="true" />

      <p data-reveal="hint" className="hero__scroll-hint" aria-hidden="true">
        SCROLL TO ENGAGE
      </p>
    </section>
  );
}
