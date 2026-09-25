/**
 * The vehicle's node contract.
 *
 * Read off the actual GLB hierarchy rather than guessed — see the inspection notes
 * in `docs/vehicle-asset.md`. Every name below exists in
 * `public/models/fenomeno.glb`, exported from Blender as `pnutfenomeno`.
 *
 * Why classification is needed at all: the exporter shares one material
 * (`przemo_l`) between the headlights, the daytime running lights, the tail lights
 * and the brake light. Driving emissive on that material would light the front and
 * the rear together, which makes the headlight beat of the opening meaningless. So
 * the meshes are grouped by name and given cloned materials, and each group is then
 * controllable on its own.
 *
 * If the asset is ever re-exported, this is the only file that needs revisiting.
 */

/** Front lighting. Lit during SCENE 03/04, stays on for the drive. */
export const HEADLIGHT_NODES = [
  'headlight_l',
  'headlight_r',
  'light_part',
  'lightsolid',
  'DRL',
  'extralight_1',
  'extralight_2',
] as const;

/** Rear lighting. Rises with braking and with speed. */
export const TAILLIGHT_NODES = [
  'taillight_l',
  'taillight_r',
  'brakelight_m',
  'reversinglight_l',
  'reversinglight_r',
] as const;

/** Cabin instrumentation — the first thing to wake in SCENE 03. */
export const INTERIOR_GLOW_NODES = ['ex', 'misc_f', 'dash', 'dashboard', 'dials', 'LED'] as const;

/** Lenses and windows. Transmissive, never lit directly. */
export const GLASS_NODES = [
  'window_lf',
  'window_rf',
  'window_lm',
  'window_rm',
  'window_lr',
  'window_rr',
  'windows',
  'windscreen',
  'windscreen_r',
  'headlight_glass',
  'taillight_glass',
  'lightglass',
] as const;

/**
 * Painted bodywork. Gets the dark metallic treatment.
 *
 * Deliberately excludes `carbon_body` and `chassis` — those are already matte
 * carbon and structural black in the source, and forcing them to mirror finish
 * flattens the contrast between panel and weave that makes the car read.
 */
export const BODY_PAINT_NODES = [
  'bodyshell',
  'bodyshell2',
  'bodyshell3',
  'body1',
  'bonnet1',
  'bonnet_part',
  'boot1',
  'door_dside_f',
  'door_pside_f',
  'scoop',
  'misc_a',
  'wing_7',
] as const;

/**
 * Wheels.
 *
 * The asset does NOT provide four separate wheel meshes. It provides two — one per
 * axle — each containing the left and right wheel merged, sitting at the scene root
 * rather than parented to the car. The `wheel_lf`/`rf`/`lr`/`rr` nodes exist but are
 * empty skeleton joints with nothing skinned to them, so rotating those does
 * nothing.
 *
 * That is fine for our purposes: both wheels on an axle turn at the same rate, so
 * rotating each axle mesh about its own centre is correct. `VehicleModel` wraps each
 * in a pivot at its measured bounding-box centre to do exactly that.
 */
export const WHEEL_AXLE_NODES = {
  front: 'Flcaliper1_Caliper_0',
  rear: 'Rlcaliper1_Caliper_0',
} as const;

/** Low-poly collision proxies left in by the exporter. Hidden — never rendered. */
export const COLLISION_SUFFIX = '.col';

export type VehiclePartGroup =
  | 'headlight'
  | 'taillight'
  | 'interior'
  | 'glass'
  | 'bodyPaint'
  | 'wheel'
  | 'other';

const LOOKUP = new Map<string, VehiclePartGroup>();
for (const name of HEADLIGHT_NODES) LOOKUP.set(name, 'headlight');
for (const name of TAILLIGHT_NODES) LOOKUP.set(name, 'taillight');
for (const name of INTERIOR_GLOW_NODES) LOOKUP.set(name, 'interior');
for (const name of GLASS_NODES) LOOKUP.set(name, 'glass');
for (const name of BODY_PAINT_NODES) LOOKUP.set(name, 'bodyPaint');
LOOKUP.set(WHEEL_AXLE_NODES.front, 'wheel');
LOOKUP.set(WHEEL_AXLE_NODES.rear, 'wheel');

export function classifyNode(name: string): VehiclePartGroup {
  return LOOKUP.get(name) ?? 'other';
}

/** Asset-space facts, measured from the GLB. */
export const VEHICLE_METRICS = {
  /** Bounding box of the source model, in metres. */
  length: 4.94,
  width: 2.25,
  height: 1.26,
  /** Nose points −Z, matching the scene's forward convention. No rotation needed. */
  forward: -1,
} as const;
