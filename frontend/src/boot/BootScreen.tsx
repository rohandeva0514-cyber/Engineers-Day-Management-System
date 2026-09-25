import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { gsap } from '@/animations/gsap';
import { scrambleTo } from '@/animations/scramble';
import { SystemBackdrop } from '@/components/hud/SystemBackdrop';
import { BOOT_LINES, BOOT_META, LOG_COLUMN, PROGRESS_CELLS } from '@/data/bootSequence';
import { prefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import '@/styles/boot.css';

type Phase = 'hold' | 'identity' | 'log' | 'progress' | 'ready' | 'ignite';

interface BootScreenProps {
  /** Called once the ignition glitch has fully resolved into the title card. */
  onComplete: () => void;
}

/** `BOOTING SYSTEM` -> `BOOTING SYSTEM....................` */
function leader(label: string): string {
  return label + '.'.repeat(Math.max(3, LOG_COLUMN - label.length));
}

function bar(percent: number): string {
  const filled = Math.round((percent / 100) * PROGRESS_CELLS);
  return '█'.repeat(filled) + '░'.repeat(PROGRESS_CELLS - filled);
}

/**
 * The initialization screen. Runs before anything else on the front door.
 *
 * One GSAP timeline owns the whole sequence and React only renders the state it
 * reports. That split matters: the timeline can be scrubbed, fast-forwarded or
 * killed from one place, and there is no chain of setTimeouts to leak if the
 * visitor navigates away mid-boot.
 *
 * Two inputs, both mapped to the same gesture (Enter or click):
 *   before READY  -> fast-forward. Nobody should be held hostage by a boot log.
 *   at READY      -> ignite, and hand off to the site.
 */
export function BootScreen({ onComplete }: BootScreenProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const timelineRef = useRef<gsap.core.Timeline | null>(null);
  const enterRef = useRef<HTMLButtonElement>(null);
  const igniteContextRef = useRef<gsap.Context | null>(null);
  const ignitedRef = useRef(false);

  // A reduced-motion visitor starts at the end: the same console, already
  // settled, prompt live, nothing to wait for. Derived in the initializers
  // rather than set from an effect so that state is correct on the very first
  // render — there is no frame in which the sequence looks like it is running.
  const settled = prefersReducedMotion();

  const [phase, setPhase] = useState<Phase>(settled ? 'ready' : 'hold');
  const [shown, setShown] = useState(settled ? BOOT_LINES.length : 0);
  const [resolved, setResolved] = useState(settled ? BOOT_LINES.length : 0);
  const [progress, setProgress] = useState(settled ? 100 : 0);

  // --- the boot timeline ---------------------------------------------------
  useLayoutEffect(() => {
    if (prefersReducedMotion()) return;

    const context = gsap.context(() => {
      const counter = { value: 0 };
      const tl = gsap.timeline();

      tl.to({}, { duration: 0.45 })
        .call(() => setPhase('identity'))
        .to({}, { duration: 1.1 })
        .call(() => setPhase('log'));

      BOOT_LINES.forEach((line, index) => {
        tl.call(() => setShown(index + 1))
          .to({}, { duration: line.work })
          .call(() => setResolved(index + 1))
          .to({}, { duration: 0.12 });
      });

      tl.call(() => setPhase('progress'))
        .to(counter, {
          value: 100,
          duration: 1.5,
          // Stepped, not smooth. A continuously interpolating bar reads as a
          // tween; a bar that jumps in discrete increments reads as work
          // completing. 24 steps is coarse enough to see, fine enough to flow.
          ease: 'steps(24)',
          onUpdate: () => setProgress(Math.round(counter.value)),
        })
        .call(() => {
          setProgress(100);
          setPhase('ready');
        });

      timelineRef.current = tl;
    }, rootRef);

    return () => {
      context.revert();
      timelineRef.current = null;
    };
  }, []);

  // Move focus to the prompt the moment it becomes live, so Enter works without
  // the visitor having to tab anywhere.
  useEffect(() => {
    if (phase === 'ready') enterRef.current?.focus();
  }, [phase]);

  // --- ignition ------------------------------------------------------------
  const ignite = useCallback(() => {
    if (ignitedRef.current) return;
    ignitedRef.current = true;
    setPhase('ignite');

    const root = rootRef.current;
    if (root === null || prefersReducedMotion()) {
      onComplete();
      return;
    }

    igniteContextRef.current = gsap.context(() => {
      const kernel = root.querySelector<HTMLElement>('[data-title-kernel]');
      // `gsap.utils.toArray` is not scoped by the surrounding context the way
      // tween selector strings are, so the root is passed explicitly.
      const titleLines = gsap.utils.toArray<HTMLElement>('[data-title-line]', root);

      const tl = gsap.timeline({ onComplete });

      // 1. The console takes a hit and breaks up.
      tl.to('[data-console]', {
        duration: 0.14,
        opacity: 0,
        scale: 1.06,
        filter: 'blur(6px)',
        ease: 'power2.in',
      })
        // 2. Signal loss: two hard white frames, no fade. A fade here would read
        //    as a slide transition; the whole point is that it should not.
        .set('[data-flash]', { opacity: 1 })
        .set('[data-flash]', { opacity: 0 }, '+=0.05')
        .set('[data-flash]', { opacity: 0.85 }, '+=0.04')
        .to('[data-flash]', { duration: 0.22, opacity: 0, ease: 'power2.out' })

        // 3. The title card assembles out of the distortion.
        .set('[data-titlecard]', { visibility: 'visible' }, '<')
        .fromTo(
          titleLines,
          { clipPath: 'inset(0 0 100% 0)', y: 26, opacity: 0 },
          {
            clipPath: 'inset(0 0 0% 0)',
            y: 0,
            opacity: 1,
            duration: 0.72,
            stagger: 0.12,
            ease: 'expo.out',
          },
          '<0.06',
        );

      if (kernel !== null) {
        tl.add(scrambleTo(kernel, 'MIT TECH KERNEL', { duration: 0.7 }), '<0.1');
      }

      // 4. Hold on the card, then hand the viewport to the site.
      tl.to({}, { duration: 0.85 }).to(root, {
        duration: 0.55,
        opacity: 0,
        ease: 'power2.inOut',
      });
    }, rootRef);
  }, [onComplete]);

  // The ignition context outlives the boot timeline's own effect, so it gets its
  // own unmount cleanup. Without this, navigating away mid-glitch leaves tweens
  // running against detached nodes.
  useEffect(() => () => igniteContextRef.current?.revert(), []);

  // --- one gesture, two meanings ------------------------------------------
  const advance = useCallback(() => {
    if (phase === 'ready') {
      ignite();
      return;
    }
    if (phase === 'ignite') return;

    // Fast-forward rather than jump: the sequence still plays, just at speed, so
    // the visitor sees what they skipped instead of it vanishing.
    timelineRef.current?.timeScale(7);
  }, [phase, ignite]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      // The prompt is a real button; when it has focus let the click path handle
      // it so this never fires twice.
      if (document.activeElement === enterRef.current) return;
      event.preventDefault();
      advance();
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [advance]);

  const showLog = phase !== 'hold' && phase !== 'identity';
  const showProgress = phase === 'progress' || phase === 'ready';

  return (
    <div
      ref={rootRef}
      className="boot"
      data-phase={phase}
      onPointerDown={advance}
      role="region"
      aria-label="System initialization"
    >
      <SystemBackdrop variant="boot" />
      <div data-flash className="boot__flash" aria-hidden="true" />

      <div data-console className="boot__console">
        <header className="boot__identity" data-glitch="MIT TECH KERNEL">
          <span className="boot__identity-text">MIT TECH KERNEL</span>
        </header>

        <dl className="boot__meta">
          {BOOT_META.map(([key, value]) => (
            <div key={key} className="boot__meta-row">
              <dt>{key}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>

        {/* aria-live rather than a visual-only log: a screen reader user hears the
            same sequence, one line at a time, as it resolves. */}
        {/* The log reserves its full height up front, so lines fill a fixed block
            instead of pushing the console around as they arrive. */}
        <ol
          className="boot__log"
          style={{ '--boot-log-lines': BOOT_LINES.length } as CSSProperties}
          aria-live="polite"
          aria-busy={phase !== 'ready'}
        >
          {showLog &&
            BOOT_LINES.slice(0, shown).map((line, index) => (
              <li key={line.label} className="boot__line">
                <span className="boot__prompt" aria-hidden="true">
                  &gt;
                </span>
                <span className="boot__label">{leader(line.label)}</span>
                <span className="boot__status" data-state={index < resolved ? line.status : 'PENDING'}>
                  {index < resolved ? line.status : '··'}
                </span>
              </li>
            ))}
        </ol>

        <div className="boot__progress" data-visible={showProgress} aria-hidden={!showProgress}>
          <span className="boot__bar" data-tabular>
            [{bar(progress)}]
          </span>
          <span className="boot__percent" data-tabular>
            {String(progress).padStart(3, '0')}%
          </span>
        </div>

        <div className="boot__ready" data-visible={phase === 'ready' || phase === 'ignite'}>
          <p className="boot__ready-text">SYSTEM READY</p>
          <button ref={enterRef} type="button" className="boot__enter" onClick={advance}>
            [ PRESS ENTER TO START ]
          </button>
        </div>
      </div>

      <div data-titlecard className="boot__titlecard" aria-hidden={phase !== 'ignite'}>
        <p data-title-line data-title-kernel className="boot__title-kernel">
          MIT TECH KERNEL
        </p>
        <h1 data-title-line className="boot__title-main">
          Engineers&rsquo; Day
        </h1>
        <p data-title-line className="boot__title-year">
          2026
        </p>
      </div>
    </div>
  );
}
