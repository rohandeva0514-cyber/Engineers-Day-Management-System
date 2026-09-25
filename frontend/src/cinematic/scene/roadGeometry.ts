import * as THREE from 'three';
import { arterial } from '../world/spine';

/**
 * The road surface.
 *
 * Built by hand rather than with `ExtrudeGeometry(shape, { extrudePath })`, which
 * orients the cross-section using Frenet frames — those roll around the curve and
 * produced a road standing on its edge partway down the corridor.
 *
 * Here the ribbon is swept with an explicit world up vector, so the surface is
 * guaranteed flat and level no matter how the Arterial bends. The V coordinate
 * runs along the road so lane markings and wetness can tile down its length.
 */
export function buildRoadGeometry(halfWidth = 9, steps = 400): THREE.BufferGeometry {
  const positions = new Float32Array((steps + 1) * 2 * 3);
  const uvs = new Float32Array((steps + 1) * 2 * 2);
  const indices: number[] = [];

  const point = new THREE.Vector3();
  const tangent = new THREE.Vector3();
  const side = new THREE.Vector3();
  const up = new THREE.Vector3(0, 1, 0);

  for (let i = 0; i <= steps; i += 1) {
    const t = i / steps;
    arterial.getPointAt(t, point);
    arterial.getTangentAt(t, tangent).normalize();

    // Side vector from the world up, not from the curve's own normal. This is the
    // whole fix: the road can never roll.
    side.crossVectors(tangent, up).normalize();

    const base = i * 2;

    positions[base * 3] = point.x - side.x * halfWidth;
    positions[base * 3 + 1] = point.y + 0.01;
    positions[base * 3 + 2] = point.z - side.z * halfWidth;

    positions[(base + 1) * 3] = point.x + side.x * halfWidth;
    positions[(base + 1) * 3 + 1] = point.y + 0.01;
    positions[(base + 1) * 3 + 2] = point.z + side.z * halfWidth;

    uvs[base * 2] = 0;
    uvs[base * 2 + 1] = t * 90;
    uvs[(base + 1) * 2] = 1;
    uvs[(base + 1) * 2 + 1] = t * 90;

    if (i < steps) {
      const a = base;
      const b = base + 1;
      const c = base + 2;
      const d = base + 3;
      indices.push(a, c, b, b, c, d);
    }
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
  geometry.setIndex(indices);
  geometry.computeVertexNormals();
  return geometry;
}
