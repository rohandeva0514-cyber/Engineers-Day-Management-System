import { gsap } from 'gsap';
import { worldState } from '../world/worldState';
import { engineAudio } from '../audio/engineAudio';

/**
 * The opening sequence.
 *
 * This autoplays — it is not scroll-driven. The brief describes the first ten
 * seconds as a thing that *happens to* the viewer: black, then a shape in the
 * dark, then the car waking, then light. Requiring a scroll to begin would make
 * the reveal a reward for input rather than an arrival, and someone who never
 * scrolls would see a black screen and leave.
 *
 * Scroll takes over afterwards, at which point this timeline is finished and the
 * drive timeline owns `worldState.u`.
 *
 * Every tween writes to the plain `worldState` object. Nothing here touches React
 * state, so the whole sequence costs zero re-renders.
 */

export interface OpeningCallbacks {
  /** Fires at the ignition beat so audio can start on the same frame. */
  onIgnition: () => void;
  /** Fires when the sequence completes and scroll should be released. */
  onComplete: () => void;
  /** Per-beat label, for the boot overlay readout. */
  onBeat: (beat: string) => void;
}

export function buildOpeningTimeline(callbacks: OpeningCallbacks): gsap.core.Timeline {
  const timeline = gsap.timeline({
    defaults: { ease: 'power2.inOut' },
    onUpdate() {
      worldState.intro = timeline.progress();
    },
    onComplete: callbacks.onComplete,
  });

  /* -- SCENE 01 · BLACK ---------------------------------------------------- */
  // Nothing moves. The darkness is allowed to sit, which is what makes the first
  // light mean anything at all.
  timeline
    .call(() => callbacks.onBeat('SYSTEM BOOT'))
    .to({}, { duration: 1.5 });

  /* -- SCENE 02 · CAR REVEAL ----------------------------------------------- */
  // A cold rim light finds the hull. Slow — this is the silhouette beat and
  // rushing it turns the protagonist into set dressing.
  timeline
    .call(() => callbacks.onBeat('VEHICLE DETECTED'))
    .to(worldState, { rimLight: 1, duration: 2.6, ease: 'power1.inOut' });

  /* -- SCENE 03 · ELECTRICAL WAKE-UP --------------------------------------- */
  timeline
    .call(() => callbacks.onBeat('ELECTRICAL SYSTEMS'))
    // Indicators stutter before they hold, the way real hardware powers up.
    .to(worldState, { electronics: 0.45, duration: 0.14 })
    .to(worldState, { electronics: 0.08, duration: 0.1 })
    .to(worldState, { electronics: 0.7, duration: 0.12 })
    .to(worldState, { electronics: 0.25, duration: 0.09 })
    .to(worldState, { electronics: 1, duration: 0.7, ease: 'power2.out' })
    .to(worldState, { lightBar: 1, duration: 0.85, ease: 'power3.out' }, '-=0.25');

  /* -- SCENE 04 · HEADLIGHTS ----------------------------------------------- */
  // The major visual moment. A hard snap to a fraction, a beat of held tension,
  // then the full beam — a single smooth ramp would read as a dimmer, not a switch.
  timeline
    .call(() => callbacks.onBeat('MAIN BEAM'))
    .to(worldState, { headlights: 0.22, duration: 0.09, ease: 'none' })
    .to(worldState, { headlights: 0.1, duration: 0.07 })
    .to(worldState, { headlights: 1, duration: 1.15, ease: 'power2.out' });

  /* -- SCENE 05 · IGNITION -------------------------------------------------- */
  timeline
    .call(() => {
      callbacks.onBeat('IGNITION');
      callbacks.onIgnition();
    })
    .to(worldState, { idleShake: 1, duration: 0.35, ease: 'power3.out' })
    .to(worldState, { idleShake: 0.32, duration: 1.1, ease: 'power2.out' });

  /* -- SCENE 06 · CITY EMERGES ---------------------------------------------- */
  // The world arrives *because* the headlights found it — light first, city second.
  timeline
    .call(() => callbacks.onBeat('ENVIRONMENT ONLINE'))
    .to(worldState, { cityReveal: 1, duration: 3.4, ease: 'power1.inOut' }, '-=0.6')
    .to(worldState, { rain: 1, duration: 2.2, ease: 'power1.in' }, '-=2.6');

  /* -- HAND-OFF ------------------------------------------------------------- */
  timeline.call(() => callbacks.onBeat('SCROLL TO DRIVE'));

  return timeline;
}

/**
 * The drive phase.
 *
 * A separate, paused timeline scrubbed by ScrollTrigger. Keeping it apart from the
 * opening means scroll position maps to road position and nothing else — the
 * reveal beats can never be re-triggered by scrolling back to the top.
 */
export function buildDriveTimeline(): gsap.core.Timeline {
  const timeline = gsap.timeline({ paused: true });

  timeline
    .to(
      worldState,
      {
        u: 1,
        duration: 10,
        ease: 'power1.in', // The car pulls away rather than starting at speed.
      },
      0,
    )
    // The idle vibration smooths out as the car gets moving.
    .to(worldState, { idleShake: 0, duration: 1.4 }, 0);

  return timeline;
}

/** Speed is d(u)/dt, smoothed. Drives wheel spin, camera FOV, HUD and engine pitch. */
export function createSpeedTracker(): (dt: number) => void {
  let previousU = 0;
  return (dt: number) => {
    if (dt <= 0) return;
    const instantaneous = Math.abs(worldState.u - previousU) / dt;
    previousU = worldState.u;
    // Heavy smoothing: raw derivatives of a scroll signal are extremely noisy and
    // would make the FOV and the audio jitter.
    worldState.speed += (Math.min(instantaneous * 22, 4) - worldState.speed) * Math.min(1, dt * 3);
    engineAudio.setSpeed(worldState.speed);
  };
}
