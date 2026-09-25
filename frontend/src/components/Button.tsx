import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'md' | 'lg';

/**
 * Angular, not rounded — the corner notch is where the industrial direction lives.
 * Only `primary` carries the signal amber; everything else stays quiet so a page
 * has exactly one obvious action.
 */
const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-signal text-void font-semibold hover:bg-[#ffc743] active:bg-[#d89f1f] ' +
    'disabled:bg-signal-soft disabled:text-faint',
  secondary:
    'bg-raised text-ink border border-line-bright hover:border-signal hover:text-signal ' +
    'disabled:text-faint disabled:border-line',
  ghost:
    'bg-transparent text-muted border border-line hover:text-ink hover:border-line-bright ' +
    'disabled:text-faint',
  danger:
    'bg-transparent text-danger border border-danger/50 hover:bg-danger/10 hover:border-danger',
};

const SIZES: Record<Size, string> = {
  md: 'h-10 px-5 text-[13px]',
  lg: 'h-12 px-7 text-sm',
};

const BASE =
  'clip-notch-sm inline-flex items-center justify-center gap-2.5 font-mono uppercase ' +
  'tracking-[0.14em] transition-colors duration-150 select-none ' +
  'disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-offset-2';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
}

export function Button({ variant = 'primary', size = 'md', className, ...rest }: ButtonProps) {
  return <button className={cn(BASE, VARIANTS[variant], SIZES[size], className)} {...rest} />;
}

interface ButtonLinkProps {
  to: string;
  variant?: Variant;
  size?: Size;
  className?: string;
  children: ReactNode;
}

/** Same surface as Button, but a real anchor — keyboard and middle-click behave. */
export function ButtonLink({ to, variant = 'primary', size = 'md', className, children }: ButtonLinkProps) {
  return (
    <Link to={to} className={cn(BASE, VARIANTS[variant], SIZES[size], className)}>
      {children}
    </Link>
  );
}
