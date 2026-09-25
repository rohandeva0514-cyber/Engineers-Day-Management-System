import { useEffect, useRef } from 'react';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import Lenis from 'lenis';
import { buildDriveTimeline } from '../timeline/openingTimeline';
import { worldState } from '../world/worldState';

gsap.registerPlugin(ScrollTrigger);

/**
 * Scroll → road position.
 *
 * The wiring that matters, and the reason it is all in one file: four libraries
 * each want to own a clock, and three of them have to be told not to.
 *
 *   wheel / touch
 *     → Lenis            (smoothing only — no RAF of its own)
 *     → ScrollTrigger    (scroll position → a single number)
 *     → drive timeline   (that number → worldState.u)
 *     → useFrame         (reads worldState, mutates transforms)
 *
 * `gsap.ticker` drives Lenis, so there is one clock for scrolling and animation.
 * Without that, Lenis runs its own RAF and the camera trails the input by a frame,
 * which is the exact symptom that makes a scroll-driven scene feel underwater.
 *
 * `lagSmoothing(0)` is important too: GSAP otherwise silently warps time after a
 * slow frame, which on a scrubbed timeline means the car jumps down the road.
 */
export function useCinematicScroll(enabled: boolean) {
  const driveTimeline = useRef<gsap.core.Timeline | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const lenis = new Lenis({
      autoRaf: false, // GSAP's ticker drives it. One clock.
      smoothWheel: true,
      lerp: 0.085,
    });

    const onScroll = () => ScrollTrigger.update();
    lenis.on('scroll', onScroll);

    const tick = (time: number) => lenis.raf(time * 1000);
    gsap.ticker.add(tick);
    gsap.ticker.lagSmoothing(0);

    const timeline = buildDriveTimeline();
    driveTimeline.current = timeline;

    const trigger = ScrollTrigger.create({
      trigger: '#drive-scroll-track',
      start: 'top top',
      end: 'bottom bottom',
      // No scrub number: Lenis already smooths the input, and the car and camera
      // have their own springs. A third smoothing stage feels like syrup and makes
      // the scrollbar disagree with the screen.
      scrub: true,
      onUpdate: (self) => {
        timeline.progress(self.progress);
        worldState.driveActive = self.progress > 0.001;
      },
    });

    return () => {
      trigger.kill();
      timeline.kill();
      lenis.off('scroll', onScroll);
      gsap.ticker.remove(tick);
      lenis.destroy();
      // A ScrollTrigger surviving this route is a scroll-jank bug on /events that
      // takes a week to attribute to the right layer.
      ScrollTrigger.getAll().forEach((instance) => instance.kill());
    };
  }, [enabled]);
}

/** Locks page scrolling while the opening sequence plays. */
export function useScrollLock(locked: boolean) {
  useEffect(() => {
    if (!locked) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.scrollTo(0, 0);

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [locked]);
}
