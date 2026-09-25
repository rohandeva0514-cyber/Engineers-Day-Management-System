/**
 * The shared mutable world state.
 *
 * This plain object is the seam between the two clocks. GSAP writes to it; the
 * render loop reads it in `useFrame` and mutates transforms and uniforms from it.
 * Nothing here ever touches React state — a `setState` at 60 Hz is the single most
 * common cause of a 30 fps R3F scene, and every value below changes every frame.
 *
 * Because it is a module singleton it is also trivially inspectable from the
 * console while tuning, which matters for a sequence whose whole quality is in the
 * timing.
 */

export interface WorldState {
  /** Opening sequence progress, 0–1. Driven by an autoplaying GSAP timeline. */
  intro: number;

  /** Drive progress along the Arterial, 0–1. Driven by scroll once the intro ends. */
  u: number;

  /** du/dt, smoothed. Drives wheel spin, camera FOV, engine pitch, HUD readout. */
  speed: number;

  /* --- reveal channels, each 0–1, animated by the opening timeline --------- */

  /** Cold rim light that first separates the car from the black. */
  rimLight: number;
  /** Dashboard and sill indicators waking up. */
  electronics: number;
  /** The continuous front light bar. */
  lightBar: number;
  /** Headlight beam intensity. The major visual moment. */
  headlights: number;
  /** How much of the city has emerged from the fog. */
  cityReveal: number;
  /** Rain density. */
  rain: number;
  /** Engine idle vibration amplitude. */
  idleShake: number;

  /** Set true once the drive phase owns the scroll. */
  driveActive: boolean;
}

export const worldState: WorldState = {
  intro: 0,
  u: 0,
  speed: 0,
  rimLight: 0,
  electronics: 0,
  lightBar: 0,
  headlights: 0,
  cityReveal: 0,
  rain: 0,
  idleShake: 0,
  driveActive: false,
};

export function resetWorldState(): void {
  worldState.intro = 0;
  worldState.u = 0;
  worldState.speed = 0;
  worldState.rimLight = 0;
  worldState.electronics = 0;
  worldState.lightBar = 0;
  worldState.headlights = 0;
  worldState.cityReveal = 0;
  worldState.rain = 0;
  worldState.idleShake = 0;
  worldState.driveActive = false;
}

/** Frame-rate independent damping. Identical behaviour at 60, 90 and 144 Hz. */
export function damp(current: number, target: number, lambda: number, dt: number): number {
  return current + (target - current) * (1 - Math.exp(-lambda * dt));
}
