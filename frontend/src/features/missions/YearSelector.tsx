import type { YearLevel } from '@/domain/types';

const YEARS: readonly { value: YearLevel; label: string; code: string }[] = [
  { value: 1, label: 'Freshers', code: 'Y1' },
  { value: 2, label: 'Second year', code: 'Y2' },
];

interface YearSelectorProps {
  value: YearLevel;
  onChange: (year: YearLevel) => void;
  /** Mission count for the selected year, shown as a live readout. */
  count: number;
}

/**
 * Which year's missions are on the board.
 *
 * Built on native radio inputs rather than styled buttons. Radios bring the
 * whole interaction model with them for free — arrow keys move between options,
 * the group is one tab stop, and a screen reader announces "2 of 2 selected"
 * without a line of ARIA. Rebuilding that on buttons means hand-writing key
 * handling and `aria-checked`, and getting it subtly wrong.
 *
 * The inputs are clipped away rather than `display: none`, because a hidden
 * input cannot receive focus and the keyboard model would go with it.
 */
export function YearSelector({ value, onChange, count }: YearSelectorProps) {
  return (
    <div className="years">
      <fieldset className="years__set">
        <legend className="years__legend">Select your year</legend>

        <div className="years__options">
          {YEARS.map((year) => {
            const id = `year-${year.value}`;
            const selected = value === year.value;

            return (
              <div key={year.value} className="years__option" data-selected={selected}>
                <input
                  id={id}
                  type="radio"
                  name="mission-year"
                  className="years__input"
                  value={year.value}
                  checked={selected}
                  onChange={() => onChange(year.value)}
                />
                <label htmlFor={id} className="years__label">
                  <span className="years__code" aria-hidden="true">
                    {year.code}
                  </span>
                  <span className="years__name">{year.label}</span>
                </label>
                <span className="years__underline" aria-hidden="true" />
              </div>
            );
          })}
        </div>
      </fieldset>

      {/* A live region, so switching year is announced rather than silently
          changing the list further down the page. */}
      <p className="years__readout" aria-live="polite">
        <span className="years__readout-key">Missions available</span>
        <span className="years__readout-value" data-tabular>
          {String(count).padStart(2, '0')}
        </span>
      </p>
    </div>
  );
}
