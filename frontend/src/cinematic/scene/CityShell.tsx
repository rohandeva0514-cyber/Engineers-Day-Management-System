import { useLayoutEffect, useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';
import { pointAt, tangentAt } from '../world/spine';
import { buildRoadGeometry } from './roadGeometry';
import { worldState } from '../world/worldState';
import { getSignTexture, getWindowTexture } from './textures';

/**
 * The city.
 *
 * Towers and signage are placed *along the Arterial* rather than scattered through
 * a volume — they are authored as a function of arc length, so the corridor always
 * has walls and nothing is ever built where the camera will not go. That is the
 * whole economy of the corridor-world decision: a few hundred instances produce a
 * city that reads as enormous, because the only part that exists is the part you
 * drive through.
 *
 * Everything is instanced. Two `InstancedMesh` draws carry the entire skyline.
 *
 * The city does not fade in with opacity — it *emerges from fog*. `cityReveal`
 * drives fog density and emissive intensity together, so the buildings arrive the
 * way they would if headlights and distance were doing the work.
 */

const ROAD_HALF_WIDTH = 9;

const up = new THREE.Vector3(0, 1, 0);
const centre = new THREE.Vector3();
const tangent = new THREE.Vector3();
const side = new THREE.Vector3();
const placement = new THREE.Vector3();
const matrix = new THREE.Matrix4();
const quaternion = new THREE.Quaternion();
const scale = new THREE.Vector3();

interface CityShellProps {
  towerCount: number;
  signCount: number;
}

export function CityShell({ towerCount, signCount }: CityShellProps) {
  const towers = useRef<THREE.InstancedMesh>(null);
  const signs = useRef<THREE.InstancedMesh>(null);
  const towerMaterial = useRef<THREE.MeshStandardMaterial>(null);
  const signMaterial = useRef<THREE.MeshBasicMaterial>(null);
  const roadMaterial = useRef<THREE.MeshStandardMaterial>(null);

  const windowTexture = useMemo(() => {
    const texture = getWindowTexture().clone();
    // Box UVs run 0–1 per face, so one un-tiled copy stretched the whole facade
    // and the "windows" came out several storeys tall.
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(2, 5);
    texture.needsUpdate = true;
    return texture;
  }, []);
  const signTexture = useMemo(() => getSignTexture(), []);

  /**
   * Deterministic layout.
   *
   * Seeded rather than `Math.random()` so the city is identical on every reload —
   * a scene whose composition changes between refreshes cannot be art-directed,
   * and a bad frame could never be reproduced.
   */
  const layout = useMemo(() => {
    let seed = 20260929;
    const random = () => {
      seed = (seed * 1664525 + 1013904223) % 4294967296;
      return seed / 4294967296;
    };

    const buildings: Array<{ u: number; offset: number; w: number; h: number; d: number }> = [];
    for (let i = 0; i < towerCount; i += 1) {
      const u = 0.02 + (i / towerCount) * 0.98;
      const leftSide = i % 2 === 0;
      const w = 8 + random() * 18;
      const d = 8 + random() * 18;
      // Offset is measured to the building's NEAR FACE, then half its width is
      // added. Offsetting to the centre let wide towers reach across the 9-unit
      // carriageway, and the camera ended up inside one.
      const clearance = ROAD_HALF_WIDTH + 13 + random() * 40;
      buildings.push({
        u: u + (random() - 0.5) * 0.008,
        offset: (clearance + w / 2) * (leftSide ? -1 : 1),
        w,
        h: 26 + random() * 120,
        d,
      });
    }

    const signage: Array<{ u: number; offset: number; w: number; h: number; tilt: number }> = [];
    for (let i = 0; i < signCount; i += 1) {
      const leftSide = i % 2 === 0;
      signage.push({
        u: 0.05 + (i / signCount) * 0.9 + (random() - 0.5) * 0.01,
        offset: (ROAD_HALF_WIDTH + 3.5 + random() * 9) * (leftSide ? -1 : 1),
        w: 2.4 + random() * 3.2,
        h: 5 + random() * 9,
        tilt: (random() - 0.5) * 0.5,
      });
    }

    return { buildings, signage };
  }, [towerCount, signCount]);

  // Instance matrices are written once. Nothing in the city moves; the camera does.
  useLayoutEffect(() => {
    const towerMesh = towers.current;
    if (towerMesh !== null) {
      layout.buildings.forEach((building, index) => {
        pointAt(building.u, centre);
        tangentAt(building.u, tangent);
        side.crossVectors(tangent, up).normalize();

        placement.copy(centre).addScaledVector(side, building.offset);
        placement.y = building.h / 2;

        quaternion.setFromAxisAngle(up, Math.atan2(tangent.x, tangent.z));
        scale.set(building.w, building.h, building.d);
        matrix.compose(placement, quaternion, scale);
        towerMesh.setMatrixAt(index, matrix);
      });
      towerMesh.instanceMatrix.needsUpdate = true;
    }

    const signMesh = signs.current;
    if (signMesh !== null) {
      layout.signage.forEach((sign, index) => {
        pointAt(sign.u, centre);
        tangentAt(sign.u, tangent);
        side.crossVectors(tangent, up).normalize();

        placement.copy(centre).addScaledVector(side, sign.offset);
        placement.y = 5 + (index % 5) * 3.4;

        quaternion.setFromAxisAngle(up, Math.atan2(tangent.x, tangent.z) + sign.tilt);
        scale.set(sign.w, sign.h, 1);
        matrix.compose(placement, quaternion, scale);
        signMesh.setMatrixAt(index, matrix);
      });
      signMesh.instanceMatrix.needsUpdate = true;
    }
  }, [layout]);

  useFrame(() => {
    const reveal = worldState.cityReveal;

    // Window light and signage rise together — the city literally becomes visible
    // as the sequence progresses rather than being faded in as a flat layer.
    if (towerMaterial.current !== null) {
      towerMaterial.current.emissiveIntensity = reveal * 1.5;
    }
    if (signMaterial.current !== null) {
      signMaterial.current.opacity = reveal * 0.92;
    }
    // Wet asphalt gains its sheen as the headlights find it.
    if (roadMaterial.current !== null) {
      roadMaterial.current.roughness = 0.42 - worldState.headlights * 0.28;
      roadMaterial.current.metalness = 0.45 + worldState.headlights * 0.45;
    }
  });

  const roadGeometry = useMemo(() => buildRoadGeometry(ROAD_HALF_WIDTH, 420), []);

  return (
    <group>
      {/* Wet asphalt. Low roughness plus high metalness is what produces the long
          smeared reflections of neon that define the look. */}
      <mesh geometry={roadGeometry} receiveShadow={false}>
        <meshStandardMaterial
          ref={roadMaterial}
          color="#05060a"
          roughness={0.42}
          metalness={0.45}
        />
      </mesh>

      {/* Ground beyond the road, so the horizon is not empty void. */}
      <mesh rotation-x={-Math.PI / 2} position={[0, -0.05, -400]}>
        <planeGeometry args={[1400, 1600]} />
        <meshStandardMaterial color="#030407" roughness={0.9} metalness={0.1} />
      </mesh>

      <instancedMesh ref={towers} args={[undefined, undefined, layout.buildings.length]}>
        <boxGeometry args={[1, 1, 1]} />
        <meshStandardMaterial
          ref={towerMaterial}
          color="#080a10"
          roughness={0.75}
          metalness={0.2}
          emissive="#ffffff"
          emissiveMap={windowTexture}
          emissiveIntensity={0}
          toneMapped={false}
        />
      </instancedMesh>

      <instancedMesh ref={signs} args={[undefined, undefined, layout.signage.length]}>
        <planeGeometry args={[1, 1]} />
        <meshBasicMaterial
          ref={signMaterial}
          map={signTexture}
          transparent
          opacity={0}
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          side={THREE.DoubleSide}
          toneMapped={false}
        />
      </instancedMesh>
    </group>
  );
}
