import * as THREE from 'three';

/**
 * Procedurally generated textures.
 *
 * Everything the city is lit by is drawn at runtime on a canvas rather than
 * downloaded. That keeps the cinematic route free of binary assets entirely, which
 * matters while the art pipeline does not exist yet — the scene can be judged on
 * composition and timing now, and real textures can replace these one at a time
 * without touching the scene graph.
 *
 * Both are built once and cached at module scope. Generating a 512² canvas per
 * instance would be a straightforward way to stall the first frame.
 */

let windowTexture: THREE.CanvasTexture | null = null;
let signTexture: THREE.CanvasTexture | null = null;

/**
 * Tower facade: a grid of lit and unlit windows.
 *
 * Deliberately sparse and irregular. A fully-lit facade reads as a texture swatch;
 * scattered lights at different intensities read as a building with people in it.
 */
export function getWindowTexture(): THREE.CanvasTexture {
  if (windowTexture !== null) return windowTexture;

  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');

  if (ctx === null) {
    windowTexture = new THREE.CanvasTexture(canvas);
    return windowTexture;
  }

  ctx.fillStyle = '#05060a';
  ctx.fillRect(0, 0, size, size);

  const cols = 16;
  const rows = 32;
  const cellW = size / cols;
  const cellH = size / rows;

  // The palette the whole city is lit from: cold blues dominant, warm and magenta
  // as rare accents. Uniform neon everywhere would flatten the depth.
  const palette = ['#4fd8e8', '#7fb0ff', '#e8f1ff', '#ff4d6d', '#ffc64f', '#b06cff'];

  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < cols; col += 1) {
      if (Math.random() > 0.34) continue;

      const colour = palette[Math.floor(Math.random() * palette.length)]!;
      ctx.globalAlpha = 0.25 + Math.random() * 0.75;
      ctx.fillStyle = colour;
      ctx.fillRect(
        col * cellW + cellW * 0.22,
        row * cellH + cellH * 0.22,
        cellW * 0.56,
        cellH * 0.5,
      );
    }
  }

  ctx.globalAlpha = 1;
  windowTexture = new THREE.CanvasTexture(canvas);
  windowTexture.wrapS = THREE.RepeatWrapping;
  windowTexture.wrapT = THREE.RepeatWrapping;
  windowTexture.colorSpace = THREE.SRGBColorSpace;
  return windowTexture;
}

/**
 * Holographic signage: stacked bars of glyph-like marks.
 *
 * Abstract on purpose — legible fake text at this distance reads as placeholder
 * copy, whereas rhythm and colour read as a city.
 */
export function getSignTexture(): THREE.CanvasTexture {
  if (signTexture !== null) return signTexture;

  const width = 128;
  const height = 256;
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');

  if (ctx === null) {
    signTexture = new THREE.CanvasTexture(canvas);
    return signTexture;
  }

  ctx.fillStyle = '#000000';
  ctx.fillRect(0, 0, width, height);

  const palette = ['#ff2d6f', '#4fd8e8', '#ffc64f', '#b06cff', '#7fb0ff'];
  const colour = palette[Math.floor(Math.random() * palette.length)]!;

  ctx.fillStyle = colour;
  for (let i = 0; i < 26; i += 1) {
    const y = 12 + i * 9;
    const w = 14 + Math.random() * 82;
    ctx.globalAlpha = 0.35 + Math.random() * 0.6;
    ctx.fillRect(width / 2 - w / 2, y, w, 3.5);
  }

  ctx.globalAlpha = 1;
  signTexture = new THREE.CanvasTexture(canvas);
  signTexture.colorSpace = THREE.SRGBColorSpace;
  return signTexture;
}

export function disposeTextures(): void {
  windowTexture?.dispose();
  signTexture?.dispose();
  windowTexture = null;
  signTexture = null;
}
