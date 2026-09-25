import { useEffect, useState } from 'react';

const QUERY = '(prefers-reduced-motion: reduce)';

/**
 * Whether this visitor has asked the OS to reduce motion.
 *
 * Read live rather than once, because the setting can be toggled mid-session and
 * the boot sequence is exactly the kind of thing someone toggles it *because of*.
 *
 * Returns `false` during SSR/first paint, which is the safe default here: the
 * components that consume it also read the query synchronously before they start
 * a timeline, so a reduced-motion user never sees a frame of animation.
 */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(prefersReducedMotion);

  useEffect(() => {
    const media = window.matchMedia(QUERY);
    const onChange = () => setReduced(media.matches);

    onChange();
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  return reduced;
}

/** Synchronous read, for code paths that run before the first effect. */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return window.matchMedia(QUERY).matches;
}
