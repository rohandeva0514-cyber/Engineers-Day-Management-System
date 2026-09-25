/**
 * The engineering schematic behind the title.
 *
 * Restraint is the entire brief here. Everything is between 4% and 9% opacity,
 * so this layer reads as atmosphere — something noticed on a second look, never
 * on the first. It is drafting vernacular, not decoration: a survey circle, an
 * off-axis perspective floor, dimension lines and a few sightlines.
 *
 * Deliberately sparse. An earlier pass had glitch columns, hazard blocks and
 * seven signal streaks in here, and the result competed with the title instead
 * of supporting it.
 *
 * Every stroke carries `[data-draw]` so the hero timeline draws the sheet with
 * one dash-offset tween. `preserveAspectRatio="slice"` crops rather than
 * letterboxes, so the geometry keeps its scale at any viewport shape.
 */

const W = 1600;
const H = 900;

/** Vanishing point — a little below and left of centre. Dead centre reads as a
    diagram; slightly off reads as a place. */
const VX = 742;
const VY = 596;

/** Tiny coordinate fragments. Three, at the edges, never near the title. */
const FRAGMENTS = [
  { x: 64, y: 208, text: 'SECT.07 // MUMBAI' },
  { x: 64, y: 226, text: '19.0760 N  72.8777 E' },
  { x: 1344, y: 806, text: 'ED26.KERNEL' },
] as const;

export function HeroEnvironment() {
  return (
    <svg
      aria-hidden="true"
      className="env"
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="xMidYMid slice"
      fill="none"
      stroke="currentColor"
      strokeWidth="1"
      vectorEffect="non-scaling-stroke"
    >
      {/* --- the floor: perspective rays, spaced by a cubic falloff so they
              crowd near the horizon the way perspective actually behaves --- */}
      <g className="env__deep">
        {Array.from({ length: 15 }, (_, i) => {
          const t = (i / 14) * 2 - 1;
          const x = VX + Math.sign(t) * t * t * 1560;
          return <line key={`ray${i}`} data-draw x1={VX} y1={VY} x2={x} y2={H + 60} />;
        })}

        {Array.from({ length: 6 }, (_, i) => {
          const y = VY + Math.pow((i + 1) / 6, 2.1) * (H - VY + 60);
          return <line key={`band${i}`} data-draw x1={-60} y1={y} x2={W + 60} y2={y} />;
        })}
      </g>

      {/* --- survey circle, centred on the title --- */}
      <g className="env__mid">
        <circle data-draw cx={800} cy={412} r={368} />
        <circle data-draw cx={800} cy={412} r={252} />

        {Array.from({ length: 48 }, (_, i) => {
          const angle = (i / 48) * Math.PI * 2;
          const major = i % 6 === 0;
          const inner = major ? 340 : 354;
          return (
            <line
              key={`tick${i}`}
              data-draw
              x1={800 + Math.cos(angle) * inner}
              y1={412 + Math.sin(angle) * inner}
              x2={800 + Math.cos(angle) * 368}
              y2={412 + Math.sin(angle) * 368}
              strokeWidth={major ? 1.3 : 0.7}
            />
          );
        })}

        {/* Dimension line with end ticks — the most legible piece of drafting
            grammar there is, and one is enough. */}
        <line data-draw x1={296} y1={136} x2={1304} y2={136} />
        <line data-draw x1={296} y1={128} x2={296} y2={144} />
        <line data-draw x1={1304} y1={128} x2={1304} y2={144} />

        {/* Sightlines running off the sheet. */}
        <line data-draw x1={-60} y1={252} x2={W + 60} y2={252} />
        <line data-draw x1={296} y1={-60} x2={296} y2={H + 60} />
        <line data-draw x1={1304} y1={-60} x2={1304} y2={H + 60} />
      </g>

      <g className="env__fragments">
        {FRAGMENTS.map((f) => (
          <text key={f.text} x={f.x} y={f.y}>
            {f.text}
          </text>
        ))}
      </g>
    </svg>
  );
}
