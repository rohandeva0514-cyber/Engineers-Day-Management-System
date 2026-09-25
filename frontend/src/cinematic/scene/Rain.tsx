import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { worldState } from '../world/worldState';

/**
 * Rain.
 *
 * One `Points` draw with the fall animated in a vertex shader — moving thousands
 * of particles on the CPU would cost more than the rest of the scene combined.
 * The streaks wrap within a box that is parented to the camera rig, so rain is
 * always where the viewer is and none is ever simulated where it cannot be seen.
 *
 * Density is driven by `worldState.rain`, so the weather arrives with the city
 * rather than being present in the opening darkness where it would give the reveal
 * away.
 */
export function Rain({ count }: { count: number }) {
  const material = useRef<THREE.ShaderMaterial>(null);

  const geometry = useMemo(() => {
    const positions = new Float32Array(count * 3);
    const speeds = new Float32Array(count);

    for (let i = 0; i < count; i += 1) {
      positions[i * 3] = (Math.random() - 0.5) * 120;
      positions[i * 3 + 1] = Math.random() * 60;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 160;
      speeds[i] = 0.6 + Math.random() * 0.8;
    }

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geo.setAttribute('aSpeed', new THREE.BufferAttribute(speeds, 1));
    return geo;
  }, [count]);

  useFrame((state) => {
    if (material.current === null) return;
    material.current.uniforms['uTime']!.value = state.clock.elapsedTime;
    material.current.uniforms['uOpacity']!.value = worldState.rain * 0.5;
  });

  if (count === 0) return null;

  return (
    <points geometry={geometry} frustumCulled={false}>
      <shaderMaterial
        ref={material}
        transparent
        depthWrite={false}
        blending={THREE.AdditiveBlending}
        uniforms={{ uTime: { value: 0 }, uOpacity: { value: 0 } }}
        vertexShader={`
          attribute float aSpeed;
          uniform float uTime;
          varying float vFade;
          void main() {
            vec3 p = position;
            // Wrap the fall within a 60-unit column; modulo keeps it seamless.
            p.y = mod(p.y - uTime * aSpeed * 26.0, 60.0);
            vFade = smoothstep(0.0, 14.0, p.y);
            vec4 mv = modelViewMatrix * vec4(p, 1.0);
            gl_Position = projectionMatrix * mv;
            gl_PointSize = 2.2 * (34.0 / -mv.z);
          }
        `}
        fragmentShader={`
          uniform float uOpacity;
          varying float vFade;
          void main() {
            gl_FragColor = vec4(0.62, 0.78, 0.95, uOpacity * vFade);
          }
        `}
      />
    </points>
  );
}
