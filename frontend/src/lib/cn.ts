/** Conditional className join. Small enough that a dependency would be silly. */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}
