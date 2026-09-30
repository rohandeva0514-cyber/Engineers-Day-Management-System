/**
 * Where the arena session token lives in the browser.
 *
 * `sessionStorage`, not `localStorage`, and it is **not** security.
 *
 * The one-attempt rule, the deadline and the language lock are all enforced by the
 * backend against a row in PostgreSQL. This token is only a convenience so that a
 * page refresh does not make a student retype their access code mid-mission. A
 * stolen, stale or forged value buys nothing: the server resolves it against a
 * stored hash and refuses anything it does not recognise.
 *
 * `sessionStorage` over `localStorage` for two reasons. It is scoped to the tab, so
 * closing the browser on a shared lab machine leaves no session behind for the next
 * person. And it is not shared between tabs, so a second tab has to check in — which
 * is exactly the takeover the backend is built to handle, rather than two tabs
 * quietly sharing one session.
 *
 * Every access is wrapped: private windows, blocked site data and disabled storage
 * all throw, and none of them should break the arena. With storage unavailable the
 * flow still works end to end — a refresh simply returns to the access screen.
 */

const STORAGE_KEY = 'mtk.arena.session.v1';

export function readSessionToken(): string | null {
  try {
    const value = window.sessionStorage.getItem(STORAGE_KEY);
    return value === null || value.trim() === '' ? null : value;
  } catch {
    return null;
  }
}

export function writeSessionToken(token: string): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, token);
  } catch {
    // Storage unavailable. The mission still runs; a refresh returns to the
    // access screen and the student retypes eight characters.
  }
}

export function clearSessionToken(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Nothing to do.
  }
}
