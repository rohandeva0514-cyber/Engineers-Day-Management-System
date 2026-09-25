import { useLayoutEffect, useRef } from 'react';
import { gsap, ScrollTrigger } from '@/animations/gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import '@/styles/progress-rail.css';

/**
 * Where you are in the experience.
 *
 * A label, a hairline that fills, and the name of the stage you are in. It is
 * a margin annotation, not a game HUD — no frame, no panel, no percentage.
 *
 * Performance note: the fill is driven by a `quickSetter` writing a transform
 * directly, and the stage label by a plain `textContent` write. Neither touches
 * React state, so a scroll does not re-render this component — or anything else
 * — on any frame.
 *
 * Hidden below the hero, hidden on compact screens, and hidden entirely under
 * reduced motion, where a scroll-linked indicator has nothing to say.
 */

interface Stage {
  /** Fraction of total page scroll at which this stage begins. */
  at: number;
  label: string;
}

/** The first stage is separate from the rest so the "current stage" lookup
    always has a definite starting value and never indexes into an empty list. */
const FIRST_STAGE: Stage = { at: 0, label: 'Title' };

const LATER_STAGES: readonly Stage[] = [
  { at: 0.26, label: 'Transition' },
  { at: 0.62, label: 'Events' },
];

export function ProgressRail() {
  const rootRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const root = rootRef.current;
    if (root === null) return;

    const context = gsap.context(() => {
      const fill = root.querySelector<HTMLElement>('[data-rail-fill]');
      const stage = root.querySelector<HTMLElement>('[data-rail-stage]');
      if (fill === null || stage === null) return;

      const setFill = gsap.quickSetter(fill, 'scaleY') as (value: number) => void;
      let current = '';

      ScrollTrigger.create({
        trigger: document.documentElement,
        start: 'top top',
        end: 'bottom bottom',
        onUpdate: (self) => {
          setFill(self.progress);

          // The last stage whose threshold has been passed wins.
          let label = FIRST_STAGE.label;
          for (const candidate of LATER_STAGES) {
            if (self.progress >= candidate.at) label = candidate.label;
          }
          if (label !== current) {
            current = label;
            stage.textContent = label;
          }
        },
      });

      // Appears once the title screen is behind you — during the hero it would
      // be reporting a journey that has not started.
      gsap.set(root, { opacity: 0, x: -8 });
      ScrollTrigger.create({
        trigger: document.documentElement,
        start: 'top top-=60',
        end: 'bottom bottom',
        onEnter: () => gsap.to(root, { opacity: 1, x: 0, duration: 0.5, ease: 'expo.out' }),
        onLeaveBack: () => gsap.to(root, { opacity: 0, x: -8, duration: 0.3, ease: 'power2.in' }),
      });
    }, rootRef);

    return () => context.revert();
  }, []);

  return (
    <div ref={rootRef} className="rail" aria-hidden="true">
      <p className="rail__level">Level 01</p>
      <div className="rail__track">
        <div data-rail-fill className="rail__fill" />
      </div>
      <p data-rail-stage className="rail__stage">
        {FIRST_STAGE.label}
      </p>
    </div>
  );
}
