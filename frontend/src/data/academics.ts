import type { YearLevel } from '@/domain/types';

/**
 * Branch and division options for the registration form.
 *
 * Confirmed by the organisers. The backend stores both as free text and does not
 * validate against these lists, so correcting them is a frontend-only change
 * that needs no migration and invalidates no stored row.
 */

export const BRANCHES: readonly string[] = [
  'Computer Engineering',
  'Artificial Intelligence and Machine Learning',
  'Information Technology',
  'Electronics and Communication Engineering',
] as const;

/**
 * Divisions, which differ by year: the first year runs an extra division.
 *
 * Keyed by the participant's own year rather than by the event, because an event
 * open to both years has students of both sitting in it — the division a person
 * belongs to follows them, not the event they entered.
 */
const DIVISIONS_BY_YEAR: Record<YearLevel, readonly string[]> = {
  1: ['A', 'B', 'C', 'D', 'E'],
  2: ['A', 'B', 'C', 'D'],
};

/**
 * Offered before a year is chosen: the superset, so nothing is hidden from
 * someone filling the form top to bottom. Picking a year then narrows it, and
 * `useRegistrationForm` clears a division that the new year does not have.
 */
const DIVISIONS_COMMON: readonly string[] = ['A', 'B', 'C', 'D', 'E'];

export function divisionsForYear(year: YearLevel | null): readonly string[] {
  return year === null ? DIVISIONS_COMMON : DIVISIONS_BY_YEAR[year];
}

/** Whether a division is offered for a year. Used to drop a now-invalid choice. */
export function isDivisionValidForYear(division: string, year: YearLevel | null): boolean {
  return division === '' || divisionsForYear(year).includes(division);
}
