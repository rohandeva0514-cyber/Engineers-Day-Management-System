/**
 * The engineering schematic behind the hero.
 *
 * A blueprint that means nothing — deliberately. It is texture with structure:
 * an instrument dial, a measurement axis and a small node graph, which is enough
 * for the eye to read "technical drawing" without inviting anyone to decode it.
 *
 * Every stroke is on `[data-draw]` so the hero timeline can draw the whole thing
 * with one dash-offset tween. Kept to ~20 paths: each one is a separate tween
 * target, and a schematic with a hundred of them costs real frames for detail
 * nobody can see at 8% opacity.
 */
export function HeroSchematic() {
  return (
    <svg
      aria-hidden="true"
      className="hero__schematic"
      viewBox="0 0 400 400"
      fill="none"
      stroke="currentColor"
      strokeWidth="1"
      vectorEffect="non-scaling-stroke"
    >
      {/* --- instrument dial --- */}
      <circle data-draw cx="200" cy="200" r="150" />
      <circle data-draw cx="200" cy="200" r="118" opacity="0.5" />
      <circle data-draw cx="200" cy="200" r="66" />
      <circle data-draw cx="200" cy="200" r="4" />

      {/* --- graduation ticks: generated, not hand-written, so the spacing is
              exact and changing the count is one number --- */}
      {Array.from({ length: 24 }, (_, i) => {
        const angle = (i / 24) * Math.PI * 2;
        const long = i % 6 === 0;
        const inner = long ? 136 : 144;
        return (
          <line
            key={i}
            data-draw
            x1={200 + Math.cos(angle) * inner}
            y1={200 + Math.sin(angle) * inner}
            x2={200 + Math.cos(angle) * 150}
            y2={200 + Math.sin(angle) * 150}
            strokeWidth={long ? 1.4 : 0.8}
          />
        );
      })}

      {/* --- crosshair --- */}
      <line data-draw x1="200" y1="20" x2="200" y2="122" />
      <line data-draw x1="200" y1="278" x2="200" y2="380" />
      <line data-draw x1="20" y1="200" x2="122" y2="200" />
      <line data-draw x1="278" y1="200" x2="380" y2="200" />

      {/* --- node graph: the "system" the dial is measuring --- */}
      <path data-draw d="M 118 262 L 166 224 L 224 246 L 268 196" />
      <circle data-draw cx="118" cy="262" r="5" />
      <circle data-draw cx="166" cy="224" r="5" />
      <circle data-draw cx="224" cy="246" r="5" />
      <circle data-draw cx="268" cy="196" r="5" />

      {/* --- dimension bracket --- */}
      <path data-draw d="M 50 348 L 50 360 L 350 360 L 350 348" />
      <path data-draw d="M 200 360 L 200 370" />
    </svg>
  );
}
