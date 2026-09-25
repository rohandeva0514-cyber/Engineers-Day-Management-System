import { useCallback, useState } from 'react';
import { BootScreen } from '@/boot/BootScreen';
import { HudNav } from '@/components/hud/HudNav';
import { SystemBackdrop } from '@/components/hud/SystemBackdrop';
import { Hero } from '@/sections/Hero';

/** Per-tab, not persistent: a return visit tomorrow should get the cold boot. */
const BOOT_FLAG = 'mtk.booted';

function hasBooted(): boolean {
  try {
    return sessionStorage.getItem(BOOT_FLAG) === '1';
  } catch {
    // Private mode, blocked storage, embedded webview. Falling back to "not yet
    // booted" is the honest default — the sequence is the experience, and the
    // worst case is someone sees it twice.
    return false;
  }
}

function markBooted(): void {
  try {
    sessionStorage.setItem(BOOT_FLAG, '1');
  } catch {
    /* Nothing to do; the flag is an optimisation, not state we depend on. */
  }
}

/**
 * The front door.
 *
 * Boot screen, then the site. The boot screen only runs once per tab session —
 * it is a first-contact moment, and a visitor who bounces back here from
 * /events to check a date should not have to sit through initialization again.
 *
 * The site itself is mounted the whole time, underneath. That is deliberate:
 * fonts, styles and the hero's layout are all resolved before the ignition
 * glitch lifts, so the hand-off reveals a finished frame instead of a reflow.
 */
export function ExperienceRoute() {
  const [booting, setBooting] = useState(() => !hasBooted());

  const handleBootComplete = useCallback(() => {
    markBooted();
    setBooting(false);
  }, []);

  return (
    <>
      <SystemBackdrop variant="world" />

      {/* Hidden from assistive tech and from the tab order while the boot screen
          owns the viewport, so there is exactly one focusable thing on screen. */}
      <div aria-hidden={booting} inert={booting}>
        <HudNav />
        <main id="main" tabIndex={-1} className="relative z-10 focus:outline-none">
          <Hero active={!booting} />
        </main>
      </div>

      {booting && <BootScreen onComplete={handleBootComplete} />}
    </>
  );
}
