import { gsap } from './gsap';

/**
 * Self-drawing SVG strokes.
 *
 * GSAP's DrawSVGPlugin is a Club plugin, so this is the standard dash-offset
 * technique done properly: set `stroke-dasharray` to the stroke's own length,
 * offset it by the same amount so nothing is painted, then tween the offset to
 * zero.
 *
 * Works on any `SVGGeometryElement` — path, line, circle, rect, polyline — which
 * is why the schematic can mix shapes instead of being one giant path.
 */

interface DrawOptions {
  duration?: number;
  stagger?: number;
  ease?: string;
  delay?: number;
}

function lengthOf(element: Element): number {
  // `getTotalLength` lives on SVGGeometryElement. Anything else (a <g>, a stray
  // <text>) is skipped rather than throwing, so a selector that picks up one
  // extra node degrades to "that node just appears" instead of breaking the
  // whole timeline.
  if (typeof (element as SVGGeometryElement).getTotalLength !== 'function') return 0;
  return (element as SVGGeometryElement).getTotalLength();
}

/**
 * Puts `targets` into their undrawn state immediately, with no tween.
 *
 * Used to hold a frame before its entrance plays. Hiding strokes this way rather
 * than with opacity means the elements keep whatever opacity the stylesheet gave
 * them, so the reveal lands on the designed value instead of a hardcoded one.
 */
export function setUndrawn(targets: gsap.TweenTarget): void {
  gsap.set(targets, {
    strokeDasharray: (_index: number, target: Element) => {
      const length = lengthOf(target);
      return `${length} ${length}`;
    },
    strokeDashoffset: (_index: number, target: Element) => lengthOf(target),
  });
}

/**
 * Returns a tween that draws `targets` in. Drop it into a timeline with
 * `tl.add(drawIn(...), position)`.
 */
export function drawIn(
  targets: gsap.TweenTarget,
  { duration = 1.2, stagger = 0.04, ease = 'power2.inOut', delay = 0 }: DrawOptions = {},
): gsap.core.Tween {
  const tween: gsap.core.Tween = gsap.fromTo(
    targets,
    {
      strokeDasharray: (_index: number, target: Element) => {
        const length = lengthOf(target);
        // Two values, both the full length: one dash long enough to cover the
        // stroke and one gap long enough to hide it.
        return `${length} ${length}`;
      },
      strokeDashoffset: (_index: number, target: Element) => lengthOf(target),
    },
    {
      strokeDashoffset: 0,
      duration,
      stagger,
      ease,
      delay,
      // Leaving a dasharray on a finished stroke makes it render subtly softer
      // than a plain one, and it shows on hairlines.
      //
      // Cleared via the tween's own resolved targets, NOT the selector string:
      // this callback runs long after the enclosing `gsap.context()` has gone
      // inactive, so re-resolving the selector here would match every element in
      // the document rather than the one section that asked to be drawn.
      onComplete: () => {
        gsap.set(tween.targets(), { clearProps: 'strokeDasharray,strokeDashoffset' });
      },
    },
  );

  return tween;
}
