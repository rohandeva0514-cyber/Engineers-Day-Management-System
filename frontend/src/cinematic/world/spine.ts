import * as THREE from 'three';

/**
 * The Arterial — the single curve the whole world is authored against.
 *
 * This is the corridor-world decision from CLAUDE.md made concrete. The city is
 * not an open world: it is one road through space, and every position — the car,
 * the camera, the buildings, the districts — is expressed as a function of
 * normalised arc length `u`. That single scalar is what makes the scene
 * deterministic, scrubbable in both directions, and cheap enough to hold 60 fps.
 *
 * The opening chapter is deliberately straight: the car is stationary in darkness
 * and a curve there would only fight the reveal. The bends start once the drive
 * does, so the vehicle has something to lean into.
 */
const CONTROL_POINTS: THREE.Vector3[] = [
  new THREE.Vector3(0, 0, 0),
  new THREE.Vector3(0, 0, -60),
  new THREE.Vector3(0, 0, -140),
  new THREE.Vector3(-14, 0, -230),
  new THREE.Vector3(-30, 0, -330),
  new THREE.Vector3(-18, 0, -430),
  new THREE.Vector3(14, 0, -520),
  new THREE.Vector3(30, 0, -620),
  new THREE.Vector3(22, 0, -720),
  new THREE.Vector3(0, 0, -820),
];

export const arterial = new THREE.CatmullRomCurve3(CONTROL_POINTS, false, 'catmullrom', 0.4);

/** Reused across frames — allocating a Vector3 per frame is how GC stutter starts. */
const scratchPoint = new THREE.Vector3();
const scratchTangent = new THREE.Vector3();
const scratchAhead = new THREE.Vector3();

export function pointAt(u: number, target: THREE.Vector3): THREE.Vector3 {
  return arterial.getPointAt(THREE.MathUtils.clamp(u, 0, 1), target);
}

export function tangentAt(u: number, target: THREE.Vector3): THREE.Vector3 {
  return arterial.getTangentAt(THREE.MathUtils.clamp(u, 0, 1), target).normalize();
}

/**
 * Signed curvature, from a finite difference of the tangent.
 *
 * This is what lets the car bank into corners without any authored animation —
 * the road's own geometry produces the lean.
 */
export function curvatureAt(u: number): number {
  const delta = 0.004;
  const a = THREE.MathUtils.clamp(u - delta, 0, 1);
  const b = THREE.MathUtils.clamp(u + delta, 0, 1);

  tangentAt(a, scratchTangent);
  tangentAt(b, scratchAhead);

  // Cross-product Y component gives the turn direction and magnitude.
  return scratchTangent.x * scratchAhead.z - scratchTangent.z * scratchAhead.x;
}

/** A point further along the road, for the camera to look towards. */
export function lookAheadAt(u: number, distance: number, target: THREE.Vector3): THREE.Vector3 {
  return pointAt(THREE.MathUtils.clamp(u + distance, 0, 1), target);
}

export { scratchPoint };
