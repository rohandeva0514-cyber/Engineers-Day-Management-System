import { useEffect } from 'react';
import { useFrame, useThree } from '@react-three/fiber';
import * as THREE from 'three';
import { worldState } from '../world/worldState';

/**
 * The environment map the car's paint reflects.
 *
 * Without one, a metalness-0.9 material has nothing to mirror and renders as a
 * black hole in the frame no matter how many lights are added — which is exactly
 * what makes most dark-car scenes look like plastic.
 *
 * It is generated procedurally rather than downloaded: a handful of emissive panels
 * in the palette the city actually uses — cold cyan overhead and to one side,
 * magenta to the other, a dim warm strip low down — rendered once through
 * `PMREMGenerator`. That costs one frame at startup, adds no network request and no
 * HDR asset, and gives the bodywork believable neon smears as it turns.
 *
 * The intensity is tied to `cityReveal`, so the paint has nothing to reflect while
 * the world is still dark and gains it as the city emerges. A car mirroring a city
 * that has not appeared yet would give the reveal away.
 */
export function NeonEnvironment() {
  const { gl, scene } = useThree();

  useEffect(() => {
    const pmrem = new THREE.PMREMGenerator(gl);
    pmrem.compileEquirectangularShader();

    const source = new THREE.Scene();

    // Panels are placed, not random: one dominant cold source high and left (the
    // key the car is modelled by), a magenta bounce right, and a low warm strip
    // that catches the sills. Anything more becomes soup at this blur level.
    const panels: Array<{
      colour: string;
      intensity: number;
      position: [number, number, number];
      scale: [number, number];
    }> = [
      { colour: '#4fd8e8', intensity: 5.5, position: [-6, 7, -3], scale: [9, 9] },
      { colour: '#b06cff', intensity: 3.2, position: [7, 4, 2], scale: [7, 10] },
      { colour: '#ff2d6f', intensity: 2.4, position: [3, 2.2, 8], scale: [6, 4] },
      { colour: '#7fb0ff', intensity: 2.0, position: [0, 10, 4], scale: [12, 6] },
      { colour: '#ffb648', intensity: 1.2, position: [-4, 0.6, 6], scale: [8, 1.6] },
    ];

    const geometry = new THREE.PlaneGeometry(1, 1);
    for (const panel of panels) {
      const material = new THREE.MeshBasicMaterial({
        color: new THREE.Color(panel.colour).multiplyScalar(panel.intensity),
        side: THREE.DoubleSide,
      });
      const mesh = new THREE.Mesh(geometry, material);
      mesh.position.set(...panel.position);
      mesh.scale.set(panel.scale[0], panel.scale[1], 1);
      mesh.lookAt(0, 1, 0);
      source.add(mesh);
    }

    // A very dark ground so the underside is not lit from below.
    source.background = new THREE.Color('#04050a');

    const target = pmrem.fromScene(source, 0.04);
    scene.environment = target.texture;
    scene.environmentIntensity = 0;

    return () => {
      scene.environment = null;
      target.dispose();
      pmrem.dispose();
      geometry.dispose();
      source.traverse((object) => {
        if (object instanceof THREE.Mesh) (object.material as THREE.Material).dispose();
      });
    };
  }, [gl, scene]);

  useFrame(() => {
    // Reflections arrive with the city they are reflections of.
    scene.environmentIntensity = 0.12 + worldState.cityReveal * 0.85;
  });

  return null;
}
