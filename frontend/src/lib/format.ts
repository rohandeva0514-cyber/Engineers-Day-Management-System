/** Display formatting. Kept out of components so dates look the same everywhere. */

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';

  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

/** Zero-padded index for HUD-style numbering: 01, 02, 03. */
export function pad2(value: number): string {
  return value.toString().padStart(2, '0');
}

export function normaliseRollNo(value: string): string {
  return value.trim().toUpperCase();
}
