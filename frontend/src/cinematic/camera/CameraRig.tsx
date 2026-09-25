import { useRef } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { lookAheadAt, pointAt, tangentAt } from '../world/spine';
import { damp, worldState } from '../world/worldState';

/**
 * The camera.
 *
 * Never animated directly. Each frame it resolves a *target* and springs towards
 * it, which is what separates a camera that feels operated from one that feels
 * keyframed. Damping is exponential and frame-rate independent, so the motion is
 * identical at 60, 90 and 144 Hz.
 *
 * Two behaviours blend across the sequence:
 *
 *  - During the opening the camera is close and low, circling slightly, treating
 *    the car as a subject.
 *  - Once driving, it falls back and above into a follow rig, looking at a point
 *    *ahead* of the car whose distance scales with speed — which is most of what
 *    makes speed legible at all.
 *
 * Blending rather than cutting between them is deliberate: a hard switch between
 * rigs reads as a bug, a weighted blend over a second reads as direction.
 */

const carPosition = new THREE.Vector3();
const tangent = new THREE.Vector3();
const side = new THREE.Vector3();
const up = new THREE.Vector3(0, 1, 0);
const desired = new THREE.Vector3();
const lookTarget = new THREE.Vector3();
const heroTarget = new THREE.Vector3();
const driveTarget = new THREE.Vector3();
const currentLook = new THREE.Vector3();

export function CameraRig() {
  const camera = useThree((state) => state.camera) as THREE.PerspectiveCamera;
  const initialised = useRef(false);

  useFrame((state, delta) => {
    const dt = Math.min(delta, 0.05);
    const w = worldState;

    pointAt(w.u, carPosition);
    tangentAt(w.u, tangent);
    side.crossVectors(tangent, up).normalize();

    /* --- hero framing: low three-quarter FRONT ------------------------------
       The camera sits ahead of the car, not behind it. The first framing showed
       the tail, which wastes the reveal entirely: the headlights and the light
       bar are on the nose, and they are the whole point of scenes 03 and 04.
       A slow arc across the front quarter keeps the silhouette changing. */
    /* Front THREE-QUARTER, and the lateral offset has to be comparable to the
       forward one. An almost head-on view foreshortens the length away and any
       car — real or modelled — reads as a box. Roughly 45° is what every vehicle
       reveal ever shot uses, for exactly this reason.

       The camera also creeps inward as the sequence runs, so the framing tightens
       onto the nose just as the headlights come up. */
    const orbit = w.intro * 0.9;
    const forward = 6.4 - w.intro * 1.3;
    const lateral = 7.6 - w.intro * 2.6;
    heroTarget
      .copy(carPosition)
      .addScaledVector(tangent, forward)
      .addScaledVector(side, Math.cos(orbit) * lateral)
      .setY(0.46 + w.intro * 0.55);

    /* --- follow rig: behind and above, pulling back with speed -------------- */
    driveTarget
      .copy(carPosition)
      .addScaledVector(tangent, -(7.4 + w.speed * 1.8))
      .addScaledVector(side, 0.9)
      .setY(2.1 + w.speed * 0.35);

    // Weight shifts to the follow rig as the drive takes over.
    const driveWeight = THREE.MathUtils.smoothstep(w.u, 0.0, 0.035);
    desired.copy(heroTarget).lerp(driveTarget, driveWeight);

    /* --- look target -------------------------------------------------------- */
    /* Look-ahead distance is deliberately modest. A long throw pitched the
       camera up and pushed the car out of the bottom of frame entirely — the
       protagonist has to stay in shot, so the aim point is biased back toward
       the vehicle and sits low. */
    lookAheadAt(w.u, 0.005 + w.speed * 0.0025, lookTarget);
    lookTarget.lerp(carPosition, 0.45 + (1 - driveWeight) * 0.55);
    lookTarget.y += 0.35;

    if (!initialised.current) {
      camera.position.copy(desired);
      currentLook.copy(lookTarget);
      initialised.current = true;
    }

    // Asymmetric response: the camera catches up quickly but trails slightly, so
    // acceleration is felt rather than merely depicted.
    const lambda = 3.2 + driveWeight * 1.6;
    camera.position.x = damp(camera.position.x, desired.x, lambda, dt);
    camera.position.y = damp(camera.position.y, desired.y, lambda * 0.85, dt);
    camera.position.z = damp(camera.position.z, desired.z, lambda, dt);

    currentLook.x = damp(currentLook.x, lookTarget.x, 4.5, dt);
    currentLook.y = damp(currentLook.y, lookTarget.y, 4.5, dt);
    currentLook.z = damp(currentLook.z, lookTarget.z, 4.5, dt);
    camera.lookAt(currentLook);

    // FOV widens with speed. A small, almost subliminal amount of the sensation of
    // going fast comes from here rather than from the world moving.
    const targetFov = 46 + w.speed * 7 + (1 - driveWeight) * 2;
    if (Math.abs(camera.fov - targetFov) > 0.01) {
      camera.fov = damp(camera.fov, targetFov, 2.4, dt);
      camera.updateProjectionMatrix();
    }

    // Engine idle shake, applied after the spring so it is not smoothed away.
    if (w.idleShake > 0.001) {
      const t = state.clock.elapsedTime;
      camera.position.y += Math.sin(t * 44) * 0.006 * w.idleShake;
      camera.position.x += Math.sin(t * 37) * 0.004 * w.idleShake;
    }
  });

  return null;
}
