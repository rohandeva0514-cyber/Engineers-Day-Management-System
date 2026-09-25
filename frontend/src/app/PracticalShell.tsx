import type { ReactNode } from 'react';
import { SiteHeader } from './SiteHeader';
import { SiteFooter } from './SiteFooter';

/**
 * The practical application chrome.
 *
 * Extracted from RootLayout so the landing gate can render the practical site
 * directly when the cinematic layer is unavailable, without duplicating the shell
 * or reaching into router internals.
 */
export function PracticalShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <div aria-hidden="true" className="surface-grid pointer-events-none fixed inset-0 z-0 opacity-70" />
      <div aria-hidden="true" className="surface-scanlines pointer-events-none fixed inset-0 z-0" />

      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:bg-signal focus:px-4 focus:py-2 focus:font-mono focus:text-xs focus:uppercase focus:tracking-widest focus:text-void"
      >
        Skip to content
      </a>

      <div className="relative z-10 flex min-h-dvh flex-col">
        <SiteHeader />
        <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
          {children}
        </main>
        <SiteFooter />
      </div>
    </div>
  );
}
