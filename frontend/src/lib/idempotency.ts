/**
 * Idempotency keys for registration attempts.
 *
 * One key is generated per *attempt* and held for as long as that attempt is being
 * retried. If the network drops after the server committed but before the response
 * arrived, replaying the same key returns the original registration instead of
 * creating a second one.
 *
 * Deliberately NOT regenerated on every click — a fresh key on retry would defeat
 * the entire mechanism.
 */
export function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // Older browsers on campus machines. 80-char limit server-side; this is well under.
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
