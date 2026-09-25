import '@/styles/corner-brackets.css';

/**
 * Four corner marks that frame a section like a targeting reticle.
 *
 * One SVG per corner rather than one spanning the viewport: each is positioned
 * against its own corner, so the bracket arms stay a fixed length at every
 * screen size instead of scaling into the middle of the layout on a phone.
 *
 * The strokes are drawn on entry by the parent's timeline — `[data-bracket]`
 * paths carry a dash offset that GSAP animates to zero.
 */
export function CornerBrackets() {
  return (
    <div aria-hidden="true" className="brackets">
      {(['tl', 'tr', 'bl', 'br'] as const).map((corner) => (
        <svg key={corner} className="brackets__mark" data-corner={corner} viewBox="0 0 40 40">
          <path
            data-bracket
            d="M 1 14 L 1 1 L 14 1"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            vectorEffect="non-scaling-stroke"
          />
          {/* The tick is the detail that makes it read as an instrument rather
              than a decorative corner. */}
          <path
            data-bracket
            d="M 6 1 L 6 4"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      ))}
    </div>
  );
}
