/**
 * Device capability gate.
 *
 * Decides, once per session, whether this browser gets the full cinematic drive,
 * a reduced version of it, or the practical interface directly.
 *
 * Detection is by capability, never by user-agent string: a UA sniff is wrong the
 * day a new device ships, and being wrong here means either a student on a good
 * laptop is denied the experience or a student on a weak phone gets a 12 fps
 * slideshow instead of a registration form.
 *
 * The bar for T0 is deliberately high. A degraded cinematic is worse than an
 * honest practical page, and the practical page is the one that completes the task.
 */

export type RenderTier =
  /** Full cinematic: headlight volumetrics, rain, full instance counts. */
  | 'CINEMATIC'
  /** Reduced 3D: lower DPR, no rain, fewer instances, simpler lighting. */
  | 'REDUCED'
  /** No WebGL at all — the practical interface. */
  | 'PRACTICAL';

export interface Capability {
  tier: RenderTier;
  reason: string;
  prefersReducedMotion: boolean;
  hasWebGL: boolean;
}

/** Does a WebGL2 context actually come back? Feature detection, not inference. */
function probeWebGL(): boolean {
  if (typeof document === 'undefined') return false;
  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2', { failIfMajorPerformanceCaveat: true });
    if (gl === null) return false;

    // Release it immediately — browsers cap live contexts at a small number and
    // the probe must not consume one of them.
    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return true;
  } catch {
    return false;
  }
}

export function detectCapability(): Capability {
  if (typeof window === 'undefined') {
    return {
      tier: 'PRACTICAL',
      reason: 'No window',
      prefersReducedMotion: false,
      hasWebGL: false,
    };
  }

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const hasWebGL = probeWebGL();

  // Reduced motion is a stated preference, not a performance hint. It outranks
  // every capability signal below it.
  if (prefersReducedMotion) {
    return {
      tier: 'PRACTICAL',
      reason: 'Reduced motion preferred',
      prefersReducedMotion,
      hasWebGL,
    };
  }

  if (!hasWebGL) {
    return { tier: 'PRACTICAL', reason: 'WebGL unavailable', prefersReducedMotion, hasWebGL };
  }

  const coarsePointer = window.matchMedia('(pointer: coarse)').matches;
  const narrow = window.matchMedia('(max-width: 900px)').matches;

  // `deviceMemory` is Chromium-only and absent elsewhere; absence is not a failure,
  // so it only ever downgrades when it is present AND low.
  const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory;
  const cores = navigator.hardwareConcurrency ?? 4;

  if (narrow || (coarsePointer && (memory ?? 8) < 6)) {
    return {
      tier: 'PRACTICAL',
      reason: narrow ? 'Narrow viewport' : 'Low-memory touch device',
      prefersReducedMotion,
      hasWebGL,
    };
  }

  if (coarsePointer || (memory !== undefined && memory < 8) || cores <= 4) {
    return { tier: 'REDUCED', reason: 'Mid-tier device', prefersReducedMotion, hasWebGL };
  }

  return { tier: 'CINEMATIC', reason: 'Capable desktop', prefersReducedMotion, hasWebGL };
}

/** Per-tier render budget, read by the scene rather than scattered as conditionals. */
export interface SceneBudget {
  dpr: [number, number];
  towerCount: number;
  signCount: number;
  rainCount: number;
  lightCones: boolean;
  shadows: boolean;
}

export const BUDGETS: Record<Exclude<RenderTier, 'PRACTICAL'>, SceneBudget> = {
  CINEMATIC: {
    dpr: [1, 1.75],
    towerCount: 150,
    signCount: 64,
    rainCount: 5500,
    lightCones: true,
    shadows: false,
  },
  REDUCED: {
    dpr: [1, 1.15],
    towerCount: 70,
    signCount: 28,
    rainCount: 0,
    lightCones: false,
    shadows: false,
  },
};

/** Session override so a user can opt out of the drive and keep the choice. */
const OVERRIDE_KEY = 'mtk.render.override';

export function readOverride(): RenderTier | null {
  try {
    const value = window.sessionStorage.getItem(OVERRIDE_KEY);
    return value === 'PRACTICAL' || value === 'CINEMATIC' ? value : null;
  } catch {
    return null;
  }
}

export function writeOverride(tier: RenderTier | null): void {
  try {
    if (tier === null) window.sessionStorage.removeItem(OVERRIDE_KEY);
    else window.sessionStorage.setItem(OVERRIDE_KEY, tier);
  } catch {
    // Storage blocked. The choice simply will not persist.
  }
}
