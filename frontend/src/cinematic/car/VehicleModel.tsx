import { useMemo } from 'react';
import { useGLTF } from '@react-three/drei';
import * as THREE from 'three';
import {
  COLLISION_SUFFIX,
  WHEEL_AXLE_NODES,
  classifyNode,
  type VehiclePartGroup,
} from './vehicleParts';

const MODEL_URL = '/models/fenomeno.glb';

/** Controllable handles the kinematics layer drives each frame. */
export interface VehicleHandles {
  root: THREE.Group;
  headlightMaterials: THREE.MeshStandardMaterial[];
  taillightMaterials: THREE.MeshStandardMaterial[];
  interiorMaterials: THREE.MeshStandardMaterial[];
  /** One pivot per axle; rotate `.rotation.x` to spin both wheels on that axle. */
  wheelPivots: THREE.Group[];
  /** Local-space positions of the two headlight clusters, for aiming the spot lights. */
  headlightAnchors: THREE.Vector3[];
}

/**
 * Loads and prepares the real vehicle.
 *
 * Three jobs, none of which touch the kinematics:
 *
 *  1. **Split the shared light material.** `przemo_l` is used by the headlights AND
 *     the tail lights; without cloning, turning on the main beam would light the
 *     rear too.
 *  2. **Build wheel pivots.** The asset merges each axle's two wheels into one mesh
 *     sitting at the scene root, so rotating the mesh node spins it about the world
 *     origin. Each is re-parented under a pivot placed at its measured centre.
 *  3. **Art-direct the materials** for a wet night city: darkened paint, high
 *     metalness, low roughness so the neon smears across the panels.
 *
 * Geometry is never modified. Every one of the artist's 225,952 triangles renders.
 */
export function useVehicleModel(): { scene: THREE.Group; handles: VehicleHandles } {
  // Meshopt-compressed; drei wires the decoder for us. Draco is off.
  const gltf = useGLTF(MODEL_URL, false, true);

  const prepared = useMemo(() => {
    const scene = gltf.scene;

    const headlightMaterials: THREE.MeshStandardMaterial[] = [];
    const taillightMaterials: THREE.MeshStandardMaterial[] = [];
    const interiorMaterials: THREE.MeshStandardMaterial[] = [];
    const headlightAnchors: THREE.Vector3[] = [];

    scene.traverse((object) => {
      if (!(object instanceof THREE.Mesh) && !(object instanceof THREE.SkinnedMesh)) return;

      const name = object.name;

      // Collision proxies are a handful of untextured quads meant for a physics
      // engine. Left visible they punch flat grey holes through the glass.
      if (name.endsWith(COLLISION_SUFFIX)) {
        object.visible = false;
        return;
      }

      object.castShadow = false;
      object.receiveShadow = false;
      // The scene is one long corridor and the car is always on screen; culling it
      // per-part just risks popping at the frame edges.
      object.frustumCulled = false;

      const group = classifyNode(name);

      const materials = Array.isArray(object.material) ? object.material : [object.material];
      const rebuilt = materials.map((source) => {
        if (!(source instanceof THREE.MeshStandardMaterial)) return source;

        // Clone per group so a shared source material can be driven independently.
        const material = source.clone();
        applyTreatment(material, group);

        if (group === 'headlight') headlightMaterials.push(material);
        if (group === 'taillight') taillightMaterials.push(material);
        if (group === 'interior') interiorMaterials.push(material);
        return material;
      });

      object.material = Array.isArray(object.material) ? rebuilt : rebuilt[0]!;

      if (group === 'headlight' && (name === 'headlight_l' || name === 'headlight_r')) {
        const box = new THREE.Box3().setFromObject(object);
        headlightAnchors.push(box.getCenter(new THREE.Vector3()));
      }
    });

    /* -- wheel pivots ------------------------------------------------------- */
    const wheelPivots: THREE.Group[] = [];
    for (const axleName of [WHEEL_AXLE_NODES.front, WHEEL_AXLE_NODES.rear]) {
      const mesh = scene.getObjectByName(axleName);
      if (mesh === undefined) continue;

      // Measure before re-parenting — the box has to be in the same space the
      // pivot will live in.
      const box = new THREE.Box3().setFromObject(mesh);
      const centre = box.getCenter(new THREE.Vector3());

      const pivot = new THREE.Group();
      pivot.name = `${axleName}__pivot`;
      pivot.position.copy(centre);

      const parent = mesh.parent ?? scene;
      parent.add(pivot);
      pivot.add(mesh);
      // Cancel the pivot's offset so the wheels stay exactly where the artist put
      // them; only the rotation centre has changed.
      mesh.position.sub(centre);

      wheelPivots.push(pivot);
    }

    /* -- sit the car on the road -------------------------------------------- */
    // The source model's lowest point is a little under y = 0. Measuring and
    // offsetting is more robust than a magic number, and survives a re-export.
    const bounds = new THREE.Box3().setFromObject(scene);
    scene.position.y -= bounds.min.y;

    return {
      scene,
      handles: {
        root: scene,
        headlightMaterials,
        taillightMaterials,
        interiorMaterials,
        wheelPivots,
        headlightAnchors,
      } satisfies VehicleHandles,
    };
  }, [gltf.scene]);

  return prepared;
}

/**
 * Per-group material treatment.
 *
 * The brief asks for a dark metallic car in a wet neon city without turning it into
 * a glowing toy. So: the paint is darkened and polished so it *reflects* the city
 * rather than emitting anything, and only the lamps are emissive.
 */
function applyTreatment(material: THREE.MeshStandardMaterial, group: VehiclePartGroup): void {
  switch (group) {
    case 'bodyPaint':
      // Multiply the artist's albedo down rather than replacing it, so paint
      // detail and panel variation survive.
      material.color.multiplyScalar(0.34);
      material.metalness = 0.92;
      material.roughness = 0.22;
      material.envMapIntensity = 1.5;
      break;

    case 'glass':
      material.color.multiplyScalar(0.5);
      material.metalness = 0.6;
      material.roughness = 0.06;
      material.transparent = true;
      material.opacity = 0.62;
      material.envMapIntensity = 2;
      material.depthWrite = false;
      break;

    case 'headlight':
      material.emissive = new THREE.Color('#dce9ff');
      material.emissiveIntensity = 0;
      material.toneMapped = false;
      material.transparent = false;
      material.opacity = 1;
      break;

    case 'taillight':
      material.emissive = new THREE.Color('#ff1f38');
      material.emissiveIntensity = 0;
      material.toneMapped = false;
      material.transparent = false;
      material.opacity = 1;
      break;

    case 'interior':
      material.emissiveIntensity = 0;
      material.toneMapped = false;
      break;

    default:
      // Carbon, chassis, tyres, calipers: keep the source look, just make sure
      // they respond to the environment instead of reading as flat black.
      material.envMapIntensity = 1.1;
      if (material.roughness > 0.85) material.roughness = 0.85;
      break;
  }

  // The exporter marks almost everything BLEND and double-sided. Both are
  // expensive and wrong for opaque bodywork, and the blend sorting produces
  // flickering panels as the camera moves.
  if (group !== 'glass' && material.transparent && material.opacity >= 0.99) {
    material.transparent = false;
    material.depthWrite = true;
  }
  if (group === 'bodyPaint' || group === 'headlight' || group === 'taillight') {
    material.side = THREE.FrontSide;
  }
  material.needsUpdate = true;
}

/** Warm the cache so the model is resident before the opening sequence starts. */
export function preloadVehicle(): void {
  useGLTF.preload(MODEL_URL, false, true);
}

/** Frees GPU memory when the drive route unmounts. */
export function disposeVehicle(): void {
  useGLTF.clear(MODEL_URL);
}
