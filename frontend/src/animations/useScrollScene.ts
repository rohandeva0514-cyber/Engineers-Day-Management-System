import { useLayoutEffect, useRef, type RefObject } from 'react';
import { gsap, ScrollTrigger } from './gsap';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

export interface SceneContext {
  /** The section element. Selector strings inside `build` are scoped to it. */
  root: HTMLElement;
  /**
   * Breakpoint-aware registration. Anything registered through this is torn
   * down automatically when the query stops matching, so a pinned desktop
   * timeline cannot survive a resize to phone width.
   */
  media: gsap.MatchMedia;
  /** Re-exported so scenes don't each reach for the plugin import. */
  ScrollTrigger: typeof ScrollTrigger;
}

/**
 * The one way a section attaches itself to the scroll.
 *
 * Every scroll-driven section gets a ref from this hook and describes its own
 * behaviour in a callback. What the hook guarantees, so that no section has to
 * remember it:
 *
 *   - selector strings resolve inside that section only, never the document;
 *   - every tween, ScrollTrigger and pin spacer is reverted on unmount, and on
 *     any dependency change;
 *   - `matchMedia` teardown runs when a breakpoint stops matching, so desktop
 *     pinning does not leak into a resized mobile layout;
 *   - reduced motion means the callback never runs at all, and the section
 *     renders as static markup.
 *
 * That last point is why every section must be readable with no JavaScript
 * applied: the reduced-motion path is the markup, not a simplified animation.
 */
export function useScrollScene<T extends HTMLElement = HTMLElement>(
  build: (scene: SceneContext) => void,
  deps: readonly unknown[] = [],
): RefObject<T | null> {
  const ref = useRef<T>(null);

  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const root = ref.current;
    if (root === null) return;

    const media = gsap.matchMedia();
    const context = gsap.context(() => build({ root, media, ScrollTrigger }), ref);

    return () => {
      media.revert();
      context.revert();
    };
    // The caller owns the dependency list; it is spread so the hook keeps a
    // stable arity across renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return ref;
}

/** Breakpoints shared by every scene, so sections cannot disagree about them. */
export const SCENE_MEDIA = {
  /** Pinning, long scrubs, multi-stage transitions. */
  desktop: '(min-width: 48rem) and (prefers-reduced-motion: no-preference)',
  /** Short, unpinned reveals. Phones must not inherit a 300vh pinned section. */
  compact: '(max-width: 47.99rem) and (prefers-reduced-motion: no-preference)',
} as const;
