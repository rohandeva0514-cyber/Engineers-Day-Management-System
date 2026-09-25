import { gsap } from './gsap';

/**
 * Text scrambling — characters resolve out of noise into the real string.
 *
 * GSAP's own ScrambleTextPlugin is a Club (paid) plugin, so this is the small
 * honest version: ~30 lines, no dependency, and it only ever writes `textContent`
 * so there is no HTML-injection surface even if the copy later comes from an API.
 */

const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/\\|<>[]{}#*+=-_';

function noise(length: number): string {
  let out = '';
  for (let i = 0; i < length; i += 1) {
    out += GLYPHS[Math.floor(Math.random() * GLYPHS.length)];
  }
  return out;
}

export interface ScrambleOptions {
  /** Seconds the whole resolve takes. */
  duration?: number;
  /** Delay before the first character locks in. */
  delay?: number;
  /** Characters that are never scrambled (spaces read as structure, not noise). */
  preserve?: RegExp;
}

/**
 * Resolves `element` to `text`, left to right, out of random glyphs.
 * Returns the tween so callers can drop it into a timeline or kill it on unmount.
 */
export function scrambleTo(
  element: HTMLElement,
  text: string,
  { duration = 0.9, delay = 0, preserve = /\s/ }: ScrambleOptions = {},
): gsap.core.Tween {
  const state = { progress: 0 };

  return gsap.to(state, {
    progress: 1,
    duration,
    delay,
    ease: 'power2.inOut',
    onUpdate: () => {
      // Characters left of the cursor are final; the two or three around it are
      // noise; everything right of it is blank. That "resolving edge" is what
      // makes it read as decryption rather than as a typing effect.
      const settled = Math.floor(state.progress * text.length);
      const head = text.slice(0, settled);
      const tail = text
        .slice(settled)
        .split('')
        .map((char) => (preserve.test(char) ? char : ''))
        .join('');

      element.textContent = head + noise(Math.min(3, text.length - settled)) + tail.slice(3);
    },
    onComplete: () => {
      element.textContent = text;
    },
  });
}
