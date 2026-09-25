/**
 * The kernel boot log.
 *
 * Content lives here rather than inside the component so the sequence can be
 * retimed, reordered or translated without touching animation code — and so the
 * component has no copy in it at all.
 */

export interface BootLine {
  /** What the kernel claims to be doing. Padded with dot leaders at render time. */
  label: string;
  /** Resolution text. Anything other than OK gets the warning colour. */
  status: 'OK' | 'READY' | 'LOCKED';
  /** Seconds this step appears to take, before its status resolves. */
  work: number;
}

export const BOOT_LINES: readonly BootLine[] = [
  { label: 'BOOTING SYSTEM', status: 'OK', work: 0.34 },
  { label: 'LOADING EVENT MODULES', status: 'OK', work: 0.28 },
  { label: 'INITIALIZING CORE', status: 'OK', work: 0.3 },
  { label: 'CALIBRATING SYSTEM', status: 'OK', work: 0.26 },
  { label: 'MOUNTING MISSION REGISTRY', status: 'READY', work: 0.24 },
  { label: 'CONNECTION ESTABLISHED', status: 'OK', work: 0.3 },
];

/** Column the status resolves in, so the dot leaders form a clean right edge. */
export const LOG_COLUMN = 34;

/** Cells in the block progress bar. Mono, so this is also its width in ch. */
export const PROGRESS_CELLS = 26;

/** Header readouts. Static, but they establish the diegetic frame immediately. */
export const BOOT_META: readonly (readonly [string, string])[] = [
  ['KERNEL', 'MTK-2026'],
  ['PROTOCOL', 'ED-26'],
  ['SECTOR', 'MUMBAI'],
];
