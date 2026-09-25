import { useLayoutEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { gsap } from '@/animations/gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import '@/styles/hero.css';

/**
 * PLACEHOLDER — phase 3 replaces this with the full title screen.
 *
 * It exists now for one reason: to prove the boot hand-off. The glitch has to
 * resolve into something that belongs to the same machine, and the only way to
 * know it does is to build the thing it resolves into.
 *
 * What is real here: the type scale, the HUD grammar, the entrance choreography
 * and the navigation. What is not: the scroll timeline, the parallax field and
 * the mission readout values, which arrive with phases 4 and 5.
 */

const HUD_READOUT: readonly (readonly [string, string, 'live' | 'plain'])[] = [
  ['SYSTEM STATUS', 'ONLINE', 'live'],
  ['MISSIONS', '06', 'plain'],
  ['ENGINEER XP', '0000', 'plain'],
];

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

  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const context = gsap.context(() => {
      if (!active) {
        // Hold everything at its pre-entrance state rather than rendering it and
        // hiding the parent: when the boot screen lifts there is no flash of the
        // settled composition before the timeline takes over.
        gsap.set('[data-hero-rule]', { scaleX: 0 });
        gsap.set(
          '[data-hero-eyebrow], [data-hero-line], [data-hero-sub], [data-hero-cta], [data-hero-hud]',
          { opacity: 0 },
        );
        return;
      }

      const tl = gsap.timeline({ defaults: { ease: 'expo.out' } });

      // The frame draws itself before anything sits inside it — the interface
      // builds, it does not fade in.
      tl.fromTo('[data-hero-rule]', { scaleX: 0 }, { scaleX: 1, duration: 0.9, stagger: 0.08 })
        .fromTo(
          '[data-hero-eyebrow]',
          { opacity: 0, x: -14 },
          { opacity: 1, x: 0, duration: 0.6 },
          '-=0.55',
        )
        .fromTo(
          '[data-hero-line]',
          { opacity: 0, y: 38, clipPath: 'inset(0 0 100% 0)' },
          { opacity: 1, y: 0, clipPath: 'inset(0 0 0% 0)', duration: 1, stagger: 0.09 },
          '-=0.4',
        )
        .fromTo('[data-hero-sub]', { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.7 }, '-=0.6')
        .fromTo('[data-hero-cta]', { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.7 }, '-=0.5')
        .fromTo(
          '[data-hero-hud]',
          { opacity: 0, y: 10 },
          { opacity: 1, y: 0, duration: 0.6, stagger: 0.07 },
          '-=0.5',
        );
    }, rootRef);

    return () => context.revert();
  }, [active]);

  return (
    <section ref={rootRef} className="hero" aria-labelledby="hero-title">
      <div data-hero-rule className="hero__rule hero__rule--top" aria-hidden="true" />

      <div className="hero__inner">
        <p data-hero-eyebrow className="hero__eyebrow">
          <span className="hero__eyebrow-dot" aria-hidden="true" />
          MIT TECH KERNEL <span aria-hidden="true">//</span> SYSTEM 01
        </p>

        <h1 id="hero-title" className="hero__title">
          <span data-hero-line className="hero__title-line">
            Engineers&rsquo; Day
          </span>
          <span data-hero-line className="hero__title-line hero__title-line--year">
            2026
          </span>
        </h1>

        <p data-hero-line className="hero__statement">
          Engineer
          <br />
          the future.
        </p>

        <p data-hero-sub className="hero__sub">
          Build <span aria-hidden="true">·</span> Break <span aria-hidden="true">·</span> Solve{' '}
          <span aria-hidden="true">·</span> Repeat
        </p>

        <div data-hero-cta className="hero__actions">
          <Link to="/events" className="hero__cta">
            [ Explore events <span aria-hidden="true">&rarr;</span> ]
          </Link>
        </div>

        <dl className="hero__hud">
          {HUD_READOUT.map(([key, value, kind]) => (
            <div key={key} data-hero-hud className="hero__hud-cell">
              <dt className="label-tech">{key}</dt>
              <dd className="hero__hud-value" data-kind={kind} data-tabular>
                {kind === 'live' && <span className="hero__hud-pulse" aria-hidden="true" />}
                {value}
              </dd>
            </div>
          ))}
        </dl>
      </div>

      <div data-hero-rule className="hero__rule hero__rule--bottom" aria-hidden="true" />

      <p className="hero__scroll-hint" aria-hidden="true">
        SCROLL TO ENGAGE
      </p>
    </section>
  );
}
