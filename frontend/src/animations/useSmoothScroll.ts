import { useEffect } from 'react';
import Lenis from 'lenis';
import { gsap, ScrollTrigger } from './gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

/**
 * Smooth scrolling, wired to GSAP's clock.
 *
 * The integration matters more than the library. Lenis and ScrollTrigger each
 * want to be the thing that runs on every frame, and if they are left to their
 * own timers the scrub lags the scroll position by a frame or two — which reads
 * as the page feeling loose rather than as an obvious bug.
 *
 * So: Lenis is driven from `gsap.ticker` instead of its own rAF loop, and
 * ScrollTrigger is updated from Lenis's scroll event. One clock, one order of
 * operations, no drift.
 *
 * `lagSmoothing(0)` is required here. GSAP's default behaviour is to absorb a
 * long frame by pretending less time passed, which is right for tweens and
 * wrong for a scroll position that must stay pinned to real input. It is
 * restored to the stock values on cleanup so nothing else in the app inherits
 * this setting.
 *
 * Lenis drives the window scroller and transforms nothing, so ScrollTrigger
 * needs no `scrollerProxy` — the native scroll position remains the truth.
 *
 * @param enabled Pass false to run on native scroll — while the boot screen
 *   owns the viewport, for instance. Reduced motion always runs native.
 */
export function useSmoothScroll(enabled: boolean): void {
  useEffect(() => {
    if (!enabled || prefersReducedMotion()) return;

    const lenis = new Lenis({
      // Long enough to feel weighted, short enough that a flick still lands
      // where the reader expects. Past ~1.4 it starts to feel like lag.
      duration: 1.05,
      easing: (t: number) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      // Touch devices already have momentum scrolling in hardware. Doubling it
      // up makes the page feel slippery and breaks the native overscroll.
      smoothWheel: true,
      touchMultiplier: 1.6,
    });

    const onScroll = () => ScrollTrigger.update();
    lenis.on('scroll', onScroll);

    const tick = (time: number) => lenis.raf(time * 1000);
    gsap.ticker.add(tick);
    gsap.ticker.lagSmoothing(0);

    // Layout settles after fonts land; pinned sections measured before that are
    // measured against the wrong heights.
    ScrollTrigger.refresh();

    return () => {
      lenis.off('scroll', onScroll);
      gsap.ticker.remove(tick);
      gsap.ticker.lagSmoothing(500, 33);
      lenis.destroy();
    };
  }, [enabled]);
}
