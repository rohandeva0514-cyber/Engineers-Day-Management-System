import type { Event, ParticipantDraft, YearLevel } from '@/domain/types';
import { SelectField, TextField } from '@/components/Field';
import { Button } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { YEAR_LEVELS } from '@/domain/types';
import { ordinalYear } from '@/domain/rules';
import { pad2 } from '@/lib/format';

/**
 * One person's details.
 *
 * Used unchanged for a solo entrant and for every member of a ten-person Debate
 * roster — the only difference is the heading above it. That is what keeps the
 * registration form free of per-event branching.
 *
 * `fieldErrors` are keyed exactly as the backend keys them (`participants[0].email`)
 * so a 400 response can be attached to the right input with no translation layer.
 */
interface ParticipantFieldsProps {
  event: Event;
  index: number;
  value: ParticipantDraft;
  onChange: (patch: Partial<ParticipantDraft>) => void;
  onRemove?: (() => void) | undefined;
  /** Captain for team events, the sole entrant for solo ones. */
  isCaptain: boolean;
  fieldErrors: Record<string, string>;
  serverFieldErrors: Record<string, string>;
  solo: boolean;
}

export function ParticipantFields({
  event,
  index,
  value,
  onChange,
  onRemove,
  isCaptain,
  fieldErrors,
  serverFieldErrors,
  solo,
}: ParticipantFieldsProps) {
  const prefix = `participants[${index}]`;

  // Server errors win: they reflect what actually happened on submit.
  const errorFor = (field: string): string | undefined =>
    serverFieldErrors[`${prefix}.${field}`] ?? fieldErrors[`${prefix}.${field}`];

  const heading = solo ? 'Your details' : isCaptain ? 'Captain' : `Member ${pad2(index + 1)}`;

  return (
    <fieldset className="border border-line bg-panel">
      <legend className="sr-only">{heading}</legend>

      <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-2.5 sm:px-5">
        <TechLabel bright={isCaptain && !solo}>
          {heading}
          {isCaptain && !solo && <span className="ml-2 text-faint">· point of contact</span>}
        </TechLabel>

        {onRemove !== undefined && (
          <Button
            type="button"
            variant="ghost"
            onClick={onRemove}
            className="h-7 px-2.5 text-[10px]"
            aria-label={`Remove member ${index + 1}`}
          >
            Remove
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 gap-4 px-4 py-4 sm:grid-cols-2 sm:px-5 sm:py-5">
        <TextField
          label="Full name"
          required
          autoComplete={isCaptain ? 'name' : 'off'}
          value={value.fullName}
          onChange={(e) => onChange({ fullName: e.target.value })}
          error={errorFor('fullName')}
          placeholder="As it appears on college records"
        />

        <TextField
          label="Roll number"
          required
          autoComplete="off"
          spellCheck={false}
          value={value.rollNo}
          onChange={(e) => onChange({ rollNo: e.target.value })}
          error={errorFor('rollNo')}
          hint="Stored in upper case"
          placeholder="1MS24CS001"
        />

        <TextField
          label="College email"
          type="email"
          required
          inputMode="email"
          autoComplete={isCaptain ? 'email' : 'off'}
          value={value.email}
          onChange={(e) => onChange({ email: e.target.value })}
          error={errorFor('email')}
          placeholder="name@college.edu"
        />

        <SelectField
          label="Year"
          required
          value={value.yearLevel === null ? '' : String(value.yearLevel)}
          onChange={(e) =>
            onChange({
              yearLevel: e.target.value === '' ? null : (Number(e.target.value) as YearLevel),
            })
          }
          error={errorFor('yearLevel')}
        >
          <option value="">Select year…</option>
          {/* Both years are always offered, even when only one is eligible. Hiding
              the ineligible option would leave a student wondering why their year
              is missing; showing it and explaining the refusal teaches the rule. */}
          {YEAR_LEVELS.map((year) => (
            <option key={year} value={year}>
              {ordinalYear(year)} year
              {!event.eligibleYears.includes(year) ? ' — not eligible' : ''}
            </option>
          ))}
        </SelectField>
      </div>
    </fieldset>
  );
}
