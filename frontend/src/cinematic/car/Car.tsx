import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { useVehicleModel } from './VehicleModel';
import { worldState } from '../world/worldState';
import { curvatureAt, pointAt, tangentAt } from '../world/spine';

/**
 * The car.
 *
 * The vehicle is the real asset — a 225,952-triangle Lamborghini Fenomeno loaded
 * from `public/models/fenomeno.glb`. Everything around it is unchanged: the
 * kinematics below are exactly the system that was already driving the placeholder,
 * and the reveal channels it reads are the same ones the opening timeline writes.
 *
 * Still kinematic, never simulated. Position is a pure function of `worldState.u`,
 * and everything that makes it feel like a vehicle — wheel spin, body roll, squat
 * under power — is derived from that function's derivatives. A physics body under
 * scroll control would drift and reach unrecoverable states the moment someone
 * scrubbed backwards quickly, which on a scrollable page is a certainty rather
 * than an edge case.
 *
 * No React state is involved, so the opening sequence still costs zero re-renders.
 */

const position = new THREE.Vector3();
const tangent = new THREE.Vector3();
const lookTarget = new THREE.Vector3();

interface CarProps {
  lightCones: boolean;
}

export function Car({ lightCones }: CarProps) {
  const root = useRef<THREE.Group>(null);
  const tilt = useRef<THREE.Group>(null);

  const headlightLeft = useRef<THREE.SpotLight>(null);
  const headlightRight = useRef<THREE.SpotLight>(null);
  const coneLeft = useRef<THREE.Mesh>(null);
  const coneRight = useRef<THREE.Mesh>(null);
  const underglow = useRef<THREE.PointLight>(null);
  const tailGlow = useRef<THREE.PointLight>(null);

  const { scene, handles } = useVehicleModel();

  /**
   * Where the beams originate.
   *
   * Measured from the real headlight clusters, so light leaves the car where the
   * lamps actually are rather than from an offset guessed near the bumper.
   */
  const beamOrigins = useMemo<Array<[number, number, number]>>(() => {
    if (handles.headlightAnchors.length >= 2) {
      const sorted = [...handles.headlightAnchors].sort((a, b) => a.x - b.x);
      const left = sorted[0]!;
      const right = sorted[sorted.length - 1]!;
      return [
        [left.x, left.y, left.z],
        [right.x, right.y, right.z],
      ];
    }
    return [
      [-0.68, 0.6, -2.2],
      [0.68, 0.6, -2.2],
    ];
  }, [handles.headlightAnchors]);

  useFrame((_, delta) => {
    const group = root.current;
    if (group === null) return;

    const dt = Math.min(delta, 0.05);
    const state = worldState;

    /* -------- placement on the Arterial ------------------------------------ */
    pointAt(state.u, position);
    group.position.copy(position);

    tangentAt(state.u, tangent);
    lookTarget.copy(position).add(tangent);
    group.lookAt(lookTarget);

    /* -------- idle vibration, before the car moves ------------------------- */
    if (state.idleShake > 0.001) {
      const t = performance.now() * 0.001;
      group.position.y += Math.sin(t * 46) * 0.0042 * state.idleShake;
      group.rotation.z += Math.sin(t * 31) * 0.0016 * state.idleShake;
    }

    /* -------- body attitude ------------------------------------------------ */
    if (tilt.current !== null) {
      // Bank into corners, straight off the road's own curvature. Free lean.
      const bank = THREE.MathUtils.clamp(curvatureAt(state.u) * 26, -0.2, 0.2);
      tilt.current.rotation.z += (bank - tilt.current.rotation.z) * Math.min(1, dt * 4);

      // Squat under acceleration.
      const squat = THREE.MathUtils.clamp(state.speed * 0.02, 0, 0.05);
      tilt.current.rotation.x += (-squat - tilt.current.rotation.x) * Math.min(1, dt * 5);
    }

    /* -------- wheels -------------------------------------------------------
       One pivot per axle. The asset merges each axle's two wheels into a single
       mesh, which is correct anyway — wheels on an axle turn together. Spin is
       proportional to distance travelled, so they stop when the car stops and
       reverse when the scroll reverses. */
    const spin = state.speed * dt * 14;
    for (const pivot of handles.wheelPivots) {
      pivot.rotation.x -= spin;
    }

    /* -------- reveal channels ---------------------------------------------- */

    // SCENE 03 — the instruments wake first, seen through the glass.
    for (const material of handles.interiorMaterials) {
      material.emissiveIntensity = state.electronics * 1.6;
    }

    // Standing lights and DRLs rise on the light-bar beat; the main beam then
    // adds on top of them in SCENE 04 rather than replacing them.
    const front = state.lightBar * 1.6 + state.headlights * 2.6;
    for (const material of handles.headlightMaterials) {
      material.emissiveIntensity = front;
    }

    // Rear lamps brighten with speed. In the follow shot they are most of what
    // holds the car's shape against dark asphalt.
    const rear = state.lightBar * (1.4 + state.speed * 0.9);
    for (const material of handles.taillightMaterials) {
      material.emissiveIntensity = rear;
    }

    const beam = state.headlights;
    if (headlightLeft.current !== null) headlightLeft.current.intensity = beam * 85;
    if (headlightRight.current !== null) headlightRight.current.intensity = beam * 85;
    if (underglow.current !== null) underglow.current.intensity = state.electronics * 3.2;
    if (tailGlow.current !== null) tailGlow.current.intensity = rear * 1.4;

    const coneOpacity = beam * 0.5;
    for (const cone of [coneLeft.current, coneRight.current]) {
      if (cone === null) continue;
      const material = cone.material as THREE.ShaderMaterial;
      material.uniforms['uOpacity']!.value = coneOpacity;
      cone.visible = coneOpacity > 0.004;
    }
  });

  return (
    <group ref={root}>
      <group ref={tilt}>
        {/* The asset's nose already points −Z, matching the scene's forward
            convention, so no corrective rotation is needed and the model sits at
            its authored 1:1 scale in metres. */}
        <primitive object={scene} />

        <pointLight
          ref={underglow}
          position={[0, 0.25, 0]}
          color="#38e0ff"
          intensity={0}
          distance={6}
          decay={2}
        />

        <pointLight
          ref={tailGlow}
          position={[0, 0.7, 2.3]}
          color="#ff2338"
          intensity={0}
          distance={7}
          decay={2}
        />
      </group>

      {/* Headlights. Forward is −Z; the target sits well down the road so the
          beam is a long throw rather than a pool at the bumper. */}
      {beamOrigins.map((origin, index) => (
        <spotLight
          key={index}
          ref={index === 0 ? headlightLeft : headlightRight}
          position={origin}
          target-position={[origin[0] * 1.5, -0.4, -46]}
          angle={0.3}
          penumbra={0.75}
          distance={80}
          decay={1.7}
          intensity={0}
          color="#eaf4ff"
        />
      ))}

      {lightCones &&
        beamOrigins.map((origin, index) => (
          <mesh
            key={index}
            ref={index === 0 ? coneLeft : coneRight}
            position={[origin[0] * 1.15, origin[1] - 0.14, -17.6]}
            rotation-x={Math.PI / 2}
            visible={false}
          >
            <coneGeometry args={[2.6, 34, 20, 1, true]} />
            {/* A flat additive cone reads as a grey polygon against the dark.
                Fading along its length and softening the rim makes it read as
                light in fog instead of as geometry. */}
            <shaderMaterial
              transparent
              depthWrite={false}
              blending={THREE.AdditiveBlending}
              side={THREE.DoubleSide}
              uniforms={{ uOpacity: { value: 0 } }}
              vertexShader={`
                varying vec2 vUv;
                void main() {
                  vUv = uv;
                  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
                }
              `}
              fragmentShader={`
                uniform float uOpacity;
                varying vec2 vUv;
                void main() {
                  float along = pow(clamp(vUv.y, 0.0, 1.0), 2.2);
                  float rim = sin(vUv.x * 3.14159);
                  gl_FragColor = vec4(0.74, 0.86, 1.0, uOpacity * along * rim);
                }
              `}
            />
          </mesh>
        ))}
    </group>
  );
}
