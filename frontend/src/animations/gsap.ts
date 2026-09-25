import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

/**
 * The single place GSAP is configured.
 *
 * Plugins are registered exactly once at module scope — registering per-component
 * is a common source of duplicated ScrollTriggers under React StrictMode's double
 * mount, and double-registered triggers are the reason scroll timelines "randomly"
 * jump.
 */
gsap.registerPlugin(ScrollTrigger);

gsap.defaults({
  ease: 'power3.out',
  duration: 0.6,
});

/**
 * ScrollTrigger recalculates on resize by default, which on mobile fires every
 * time the URL bar collapses and causes pinned sections to shudder. Ignoring
 * resizes that only change height by a small amount keeps that quiet.
 */
ScrollTrigger.config({ ignoreMobileResize: true });

export { gsap, ScrollTrigger };

/** Shared easing vocabulary, mirrored from the CSS tokens so DOM and JS agree. */
export const EASE = {
  /** Entrances, reveals, anything arriving. */
  out: 'expo.out',
  /** Section-to-section transitions. */
  inOut: 'power4.inOut',
  /** Mechanical, stepped motion — HUD readouts, counters. */
  steps: 'none',
} as const;
