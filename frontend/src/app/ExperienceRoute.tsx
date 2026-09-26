import { useCallback, useEffect, useState } from 'react';
import { ScrollTrigger } from '@/animations/gsap';
import { useSmoothScroll } from '@/animations/useSmoothScroll';
import { BootScreen } from '@/boot/BootScreen';
import { HudNav } from '@/components/hud/HudNav';
import { ProgressRail } from '@/components/hud/ProgressRail';
import { SystemBackdrop } from '@/components/hud/SystemBackdrop';
import { Colophon } from '@/sections/Colophon';
import { Events } from '@/sections/Events';
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
 * Boot screen, then the scroll campaign. The boot screen only runs once per tab
 * session — it is a first-contact moment, and someone who bounces back here
 * from /events to check a date should not sit through initialization again.
 *
 * The campaign is mounted the whole time, underneath. That is deliberate: fonts,
 * styles and section layout are all resolved before the ignition glitch lifts,
 * so the hand-off reveals a finished frame rather than a reflow.
 */
export function ExperienceRoute() {
  const [booting, setBooting] = useState(() => !hasBooted());

  // Smooth scroll starts only once the boot screen has handed over. Running it
  // underneath a locked, full-viewport overlay would just be a rAF loop burning
  // frames on a page nobody can scroll.
  useSmoothScroll(!booting);

  // The boot screen is a modal moment: the page behind it must not move. Locked
  // on <html> rather than <body> so it holds on iOS too.
  useEffect(() => {
    if (!booting) return;
    const root = document.documentElement;
    const previous = root.style.overflow;
    root.style.overflow = 'hidden';
    return () => {
      root.style.overflow = previous;
    };
  }, [booting]);

  const handleBootComplete = useCallback(() => {
    markBooted();
    setBooting(false);
    // Every trigger was measured against a locked viewport with no scrollbar.
    // Without this they keep those start/end positions and fire at the wrong
    // scroll offsets for the rest of the session.
    requestAnimationFrame(() => ScrollTrigger.refresh());
  }, []);

  return (
    <>
      <SystemBackdrop variant="world" />

      {/* Hidden from assistive tech and from the tab order while the boot screen
          owns the viewport, so there is exactly one focusable thing on screen. */}
      <div aria-hidden={booting} inert={booting}>
        <HudNav />
        <ProgressRail />
        <main id="main" tabIndex={-1} className="relative z-10 focus:outline-none">
          <Hero active={!booting} />
          <Events />
        </main>

        {/* Outside <main>: it is the page's footer, not a section of the
            campaign, and it is where the three marks and the credit live on
            this route — the HUD can only carry them as chrome. */}
        <Colophon />
      </div>

      {booting && <BootScreen onComplete={handleBootComplete} />}
    </>
  );
}
