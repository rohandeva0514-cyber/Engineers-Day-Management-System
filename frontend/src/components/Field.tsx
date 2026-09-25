import { useId } from 'react';
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

const CONTROL =
  'w-full h-11 bg-void border border-line px-3 text-[15px] text-ink placeholder:text-faint ' +
  'transition-colors duration-150 hover:border-line-bright focus:border-signal focus:outline-none ' +
  'disabled:text-faint disabled:bg-panel';

const CONTROL_INVALID = 'border-danger hover:border-danger focus:border-danger';

interface FieldShellProps {
  label: string;
  htmlFor: string;
  error?: string | undefined;
  hint?: string | undefined;
  required?: boolean;
  children: ReactNode;
  className?: string;
}

/**
 * Label, control, hint and error as one object.
 *
 * Every control gets a real `<label for>`, and hints and errors are wired through
 * `aria-describedby` — so a screen reader announces the requirement and the failure
 * together with the field rather than leaving either stranded elsewhere on the page.
 */
function FieldShell({ label, htmlFor, error, hint, required, children, className }: FieldShellProps) {
  return (
    <div className={cn('min-w-0', className)}>
      <label htmlFor={htmlFor} className="label-tech mb-1.5 block">
        {label}
        {required === true && (
          <span aria-hidden="true" className="text-signal ml-1">
            *
          </span>
        )}
      </label>

      {children}

      {hint !== undefined && error === undefined && (
        <p id={`${htmlFor}-hint`} className="mt-1.5 text-xs text-faint">
          {hint}
        </p>
      )}
      {error !== undefined && (
        <p id={`${htmlFor}-error`} className="mt-1.5 text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
  wrapperClassName?: string;
}

export function TextField({ label, error, hint, wrapperClassName, ...rest }: TextFieldProps) {
  const id = useId();
  const describedBy =
    error !== undefined ? `${id}-error` : hint !== undefined ? `${id}-hint` : undefined;

  return (
    <FieldShell
      label={label}
      htmlFor={id}
      error={error}
      hint={hint}
      {...(rest.required === true ? { required: true } : {})}
      {...(wrapperClassName !== undefined ? { className: wrapperClassName } : {})}
    >
      <input
        id={id}
        className={cn(CONTROL, error !== undefined && CONTROL_INVALID)}
        aria-invalid={error !== undefined}
        {...(describedBy !== undefined ? { 'aria-describedby': describedBy } : {})}
        {...rest}
      />
    </FieldShell>
  );
}

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'className'> {
  label: string;
  error?: string | undefined;
  hint?: string | undefined;
  children: ReactNode;
  wrapperClassName?: string;
}

export function SelectField({
  label,
  error,
  hint,
  children,
  wrapperClassName,
  ...rest
}: SelectFieldProps) {
  const id = useId();
  const describedBy =
    error !== undefined ? `${id}-error` : hint !== undefined ? `${id}-hint` : undefined;

  return (
    <FieldShell
      label={label}
      htmlFor={id}
      error={error}
      hint={hint}
      {...(rest.required === true ? { required: true } : {})}
      {...(wrapperClassName !== undefined ? { className: wrapperClassName } : {})}
    >
      <select
        id={id}
        className={cn(CONTROL, 'cursor-pointer', error !== undefined && CONTROL_INVALID)}
        aria-invalid={error !== undefined}
        {...(describedBy !== undefined ? { 'aria-describedby': describedBy } : {})}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
}
