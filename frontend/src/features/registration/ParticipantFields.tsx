import type { Event, ParticipantDraft, YearLevel } from '@/domain/types';
import { SelectField, TextField } from '@/components/Field';
import { Button } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { YEAR_LEVELS } from '@/domain/types';
import { BRANCHES, divisionsForYear } from '@/data/academics';
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
        {/*
          The certificate name.

          Spans the full row and carries a marked notice rather than a quiet hint,
          because this is the one field on the form whose mistakes are permanent:
          everything else can be corrected by the organisers before the day, and a
          misspelt name is discovered after the certificate is printed.
        */}
        <div className="sm:col-span-2">
          <TextField
            label="Full name"
            required
            autoComplete={isCaptain ? 'name' : 'off'}
            value={value.fullName}
            onChange={(e) => onChange({ fullName: e.target.value })}
            error={errorFor('fullName')}
            placeholder="NAME FATHER NAME SURNAME"
          />
          <p className="mt-2 flex items-start gap-2.5 border-l-2 border-signal bg-signal-soft/40 px-3 py-2">
            <span aria-hidden="true" className="label-tech label-tech-bright mt-0.5 shrink-0">
              CERT
            </span>
            <span className="text-xs leading-relaxed text-muted">
              This name will be printed on your certificate. Enter it exactly as you
              want it to appear.
            </span>
          </p>
        </div>

        <TextField
          label="Roll number"
          required
          autoComplete="off"
          spellCheck={false}
          value={value.rollNo}
          onChange={(e) => onChange({ rollNo: e.target.value })}
          error={errorFor('rollNo')}
        />

        <TextField
          label="Email address"
          type="email"
          required
          inputMode="email"
          autoComplete={isCaptain ? 'email' : 'off'}
          value={value.email}
          onChange={(e) => onChange({ email: e.target.value })}
          error={errorFor('email')}
          placeholder="you@example.com"
        />

        <TextField
          label="Phone number"
          type="tel"
          required
          inputMode="tel"
          autoComplete={isCaptain ? 'tel' : 'off'}
          value={value.phone}
          onChange={(e) => onChange({ phone: e.target.value })}
          error={errorFor('phone')}
          placeholder="9876543210"
          hint="Used only to reach you about this event"
        />

        <SelectField
          label="Branch"
          required
          value={value.branch}
          onChange={(e) => onChange({ branch: e.target.value })}
          error={errorFor('branch')}
        >
          <option value="">Select branch…</option>
          {BRANCHES.map((branch) => (
            <option key={branch} value={branch}>
              {branch}
            </option>
          ))}
        </SelectField>

        <SelectField
          label="Division"
          required
          value={value.division}
          onChange={(e) => onChange({ division: e.target.value })}
          error={errorFor('division')}
        >
          <option value="">Select division…</option>
          {/* Narrows once a year is selected — the first year has E and F
              divisions and the second year does not. Choosing a year that drops
              the current division clears it, in useRegistrationForm. */}
          {divisionsForYear(value.yearLevel).map((division) => (
            <option key={division} value={division}>
              {division}
            </option>
          ))}
        </SelectField>

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
