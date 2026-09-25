import '@/styles/backdrop.css';

interface SystemBackdropProps {
  /**
   * `boot` is darker and tighter — the machine is still coming up.
   * `world` opens out: horizon glow, wider grid, more depth.
   */
  variant?: 'boot' | 'world';
}

/**
 * The ambient screen the whole site sits on.
 *
 * Six fixed layers, all `pointer-events: none`, all painted once. Nothing here
 * animates on a loop except a single very slow horizon drift and the scanline
 * sweep — a background that never stops moving is the fastest way to make a
 * cyberpunk interface feel cheap, and it costs a permanent compositor job on
 * every device.
 *
 * Rendered exactly once per route, not per section, so the texture is continuous
 * as the user scrolls rather than restarting at every section boundary.
 */
export function SystemBackdrop({ variant = 'world' }: SystemBackdropProps) {
  return (
    <div aria-hidden="true" className="backdrop" data-variant={variant}>
      <div className="backdrop__wash" />
      <div className="backdrop__horizon" />
      <div className="backdrop__grid surface-grid" />
      <div className="backdrop__grid-fine surface-grid-fine" />
      <div className="backdrop__noise" />
      <div className="backdrop__scanlines surface-scanlines" />
      <div className="backdrop__sweep" />
      <div className="backdrop__vignette surface-vignette" />
    </div>
  );
}
