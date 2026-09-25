import { useRef } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { Car } from '../car/Car';
import { CameraRig } from '../camera/CameraRig';
import { CityShell } from './CityShell';
import { Rain } from './Rain';
import { NeonEnvironment } from './NeonEnvironment';
import { worldState } from '../world/worldState';
import { createSpeedTracker } from '../timeline/openingTimeline';
import type { SceneBudget } from '../capability';

/**
 * The scene graph.
 *
 * Deliberately shallow: an environment system, the city, the car, the camera. The
 * six event districts will mount into this as streamed children later, which is
 * why `CityShell` is a sibling of the car rather than its parent — districts come
 * and go, the corridor and the vehicle do not.
 *
 * Lighting is the important part of the opening, so it lives here rather than
 * inside any one component: three lights carry the whole sequence, and each is a
 * beat.
 */
export function WorldRoot({ budget }: { budget: SceneBudget }) {
  const scene = useThree((state) => state.scene);

  const rimLight = useRef<THREE.DirectionalLight>(null);
  const fillLight = useRef<THREE.PointLight>(null);
  const skyLight = useRef<THREE.HemisphereLight>(null);
  const kickLight = useRef<THREE.PointLight>(null);
  const trackSpeed = useRef(createSpeedTracker());

  useFrame((_, delta) => {
    const dt = Math.min(delta, 0.05);
    trackSpeed.current(dt);

    const w = worldState;

    // SCENE 02 — the cold rim that first separates the car from the black.
    if (rimLight.current !== null) rimLight.current.intensity = w.rimLight * 3.4;

    // A faint bounce so the near side of the hull is not solid black once lit.
    if (fillLight.current !== null) fillLight.current.intensity = w.rimLight * 9 + w.cityReveal * 6;
    if (kickLight.current !== null) kickLight.current.intensity = w.rimLight * 5 + w.cityReveal * 4;

    // Ambient sky only arrives with the city. Before that, darkness is the point.
    if (skyLight.current !== null) skyLight.current.intensity = w.cityReveal * 0.22;

    // The city emerges from fog rather than fading in: density falls as the
    // headlights and distance do the revealing.
    const fog = scene.fog as THREE.FogExp2 | null;
    if (fog !== null) {
      fog.density = 0.055 - w.cityReveal * 0.0435;
    }
  });

  return (
    <>
      {/* Near-black with a violet bias — the ground the whole palette sits on. */}
      <color attach="background" args={['#04050a']} />
      <fogExp2 attach="fog" args={['#070912', 0.055]} />

      <hemisphereLight ref={skyLight} args={['#2a3f6b', '#05060a', 0]} />

      {/* Three lights carry the entire reveal.

          KEY (cold, high, behind-left): the edge that first separates the hull
          from the black — this is scene 02 in a single light.
          FILL (violet, low, right): stops the near flank going solid black.
          KICK (magenta, behind): a hard specular line along the shoulder, which
          is what actually makes the bodywork read as curved metal. */}
      <directionalLight ref={rimLight} position={[-8, 6, 5]} intensity={0} color="#9fdcff" />

      <pointLight
        ref={fillLight}
        position={[5.5, 1.6, 2]}
        intensity={0}
        color="#8f6cff"
        distance={26}
        decay={2}
      />

      <pointLight
        ref={kickLight}
        position={[-3.5, 1.4, -5]}
        intensity={0}
        color="#ff3d7a"
        distance={22}
        decay={2}
      />

      {/* Generated once at startup — the car's paint needs something to mirror. */}
      <NeonEnvironment />

      <CityShell towerCount={budget.towerCount} signCount={budget.signCount} />
      <Car lightCones={budget.lightCones} />
      <Rain count={budget.rainCount} />
      <CameraRig />
    </>
  );
}
