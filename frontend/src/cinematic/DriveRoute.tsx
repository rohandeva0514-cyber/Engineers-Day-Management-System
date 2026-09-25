import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Canvas } from '@react-three/fiber';
import { Link } from 'react-router-dom';
import * as THREE from 'three';
import { BUDGETS, type RenderTier, writeOverride } from './capability';
import { WorldRoot } from './scene/WorldRoot';
import { CinematicHud } from './hud/CinematicHud';
import { buildOpeningTimeline } from './timeline/openingTimeline';
import { useCinematicScroll, useScrollLock } from './scroll/useCinematicScroll';
import { engineAudio } from './audio/engineAudio';
import { resetWorldState, worldState } from './world/worldState';
import { useEvents } from '@/hooks/useEvents';
import { identityFor } from '@/data/siteContent';

/**
 * The cinematic drive.
 *
 * Two phases. The opening autoplays — black, silhouette, wake-up, headlights,
 * ignition, city — and scroll is locked while it runs. When it finishes, scroll is
 * released and ScrollTrigger owns the car's position for the drive out toward the
 * event districts.
 *
 * Nothing in the practical application knows this route exists. It imports domain
 * data and the events hook; the reverse import would be a lint error. Deleting
 * this folder leaves a working, registerable site — which is also exactly what a
 * reduced-motion or low-end visitor receives.
 */

type Phase = 'INTRO' | 'DRIVE';
type AudioState = 'LOCKED' | 'READY' | 'MUTED' | 'UNAVAILABLE';

export function DriveRoute({ tier }: { tier: Exclude<RenderTier, 'PRACTICAL'> }) {
  const budget = BUDGETS[tier];

  const [phase, setPhase] = useState<Phase>('INTRO');
  const [beat, setBeat] = useState('SYSTEM BOOT');
  const [audioState, setAudioState] = useState<AudioState>('LOCKED');
  const [contextLost, setContextLost] = useState(false);

  const timelineRef = useRef<gsap.core.Timeline | null>(null);
  const ignitionPending = useRef(false);

  useScrollLock(phase === 'INTRO');
  useCinematicScroll(phase === 'DRIVE');

  /* ---------------------------------------------------- opening sequence */

  useEffect(() => {
    resetWorldState();

    const timeline = buildOpeningTimeline({
      onBeat: setBeat,
      onIgnition: () => {
        // If audio is already unlocked, fire on this frame. If not, remember that
        // the beat has passed so the sound plays the moment consent is given —
        // rather than being lost, or firing late and out of sync with the visuals.
        if (engineAudio.getState() === 'READY') engineAudio.startEngine();
        else ignitionPending.current = true;
      },
      onComplete: () => setPhase('DRIVE'),
    });

    timelineRef.current = timeline;
    return () => {
      timeline.kill();
      timelineRef.current = null;
    };
  }, []);

  /* ------------------------------------------------------------- audio */

  const enableAudio = useCallback(async () => {
    const ok = await engineAudio.unlock();
    if (!ok) {
      setAudioState('UNAVAILABLE');
      return;
    }
    setAudioState('READY');
    // Catch up if ignition already happened while audio was locked.
    if (ignitionPending.current) {
      engineAudio.startEngine();
      ignitionPending.current = false;
    }
  }, []);

  const toggleAudio = useCallback(() => {
    if (audioState === 'LOCKED' || audioState === 'UNAVAILABLE') {
      void enableAudio();
      return;
    }
    const next = audioState === 'MUTED' ? 'READY' : 'MUTED';
    engineAudio.setMuted(next === 'MUTED');
    setAudioState(next);
  }, [audioState, enableAudio]);

  // Audio never starts without a gesture. This listens for the first one anywhere
  // on the page and offers it to the AudioContext, so a viewer who clicks or
  // scrolls naturally gets sound without ever being asked.
  useEffect(() => {
    if (audioState !== 'LOCKED') return;

    const onGesture = () => void enableAudio();
    const options = { once: true, passive: true } as const;
    window.addEventListener('pointerdown', onGesture, options);
    window.addEventListener('keydown', onGesture, options);
    window.addEventListener('wheel', onGesture, options);

    return () => {
      window.removeEventListener('pointerdown', onGesture);
      window.removeEventListener('keydown', onGesture);
      window.removeEventListener('wheel', onGesture);
    };
  }, [audioState, enableAudio]);

  useEffect(() => () => engineAudio.dispose(), []);

  /* --------------------------------------------------------------- skip */

  const skip = useCallback(() => {
    timelineRef.current?.progress(1);
    engineAudio.dispose();
  }, []);

  /** Skipping the drive entirely keeps the choice for the rest of the session. */
  const preferPractical = useCallback(() => {
    writeOverride('PRACTICAL');
  }, []);

  /* ------------------------------------------------- WebGL context loss */

  const onCanvasCreated = useCallback((state: { gl: THREE.WebGLRenderer }) => {
    const canvas = state.gl.domElement;
    const onLost = (event: Event) => {
      event.preventDefault();
      setContextLost(true);
    };
    canvas.addEventListener('webglcontextlost', onLost);
  }, []);

  // A lost context is expected on integrated GPUs under memory pressure. It must
  // never leave a black rectangle — the practical site is one link away.
  if (contextLost) {
    return <ContextLostFallback onContinue={preferPractical} />;
  }

  return (
    <div className="relative bg-[#04050a]">
      {/* The canvas is fixed; the scroll track below it provides the distance. */}
      <div className="fixed inset-0 z-0">
        <Canvas
          dpr={budget.dpr}
          gl={{
            antialias: tier === 'CINEMATIC',
            powerPreference: 'high-performance',
            alpha: false,
          }}
          camera={{ fov: 46, near: 0.1, far: 900, position: [4, 1.2, 8] }}
          onCreated={onCanvasCreated}
          shadows={false}
        >
          <Suspense fallback={null}>
            <WorldRoot budget={budget} />
          </Suspense>
        </Canvas>
      </div>

      <CinematicHud
        phase={phase}
        beat={beat}
        audioState={audioState}
        onToggleAudio={toggleAudio}
        onSkip={preferPractical}
      />

      {/* SCENE 01 title. Minimal and system-like, not a conference headline —
          and it fades as the car is revealed rather than sitting over the world. */}
      <IntroTitle phase={phase} onSkip={skip} />

      {/*
        The accessible document behind the spectacle.
        Real headings, real links, in the accessibility tree, visually hidden. A
        screen reader encounters a well-formed page that happens to have a game
        behind it, and this is the same content the practical site renders.
      */}
      <DriveDocument />

      {/* Scroll distance for the drive phase. Only present once the opening is
          done, so the page cannot be scrolled past the sequence. */}
      {phase === 'DRIVE' && <div id="drive-scroll-track" className="relative h-[700vh]" />}

      {phase === 'DRIVE' && <DistrictHandoff />}
    </div>
  );
}

/* ------------------------------------------------------------------ pieces */

function IntroTitle({ phase, onSkip }: { phase: Phase; onSkip: () => void }) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    if (phase === 'DRIVE') setVisible(false);
  }, [phase]);

  return (
    <div
      className="pointer-events-none fixed inset-0 z-10 transition-opacity duration-1000"
      style={{ opacity: visible ? 1 : 0 }}
    >
      {/* Sits low-left, clear of the vehicle. Centred, it landed across the
          bonnet and competed with the subject of the shot. */}
      <div className="absolute bottom-[22%] left-6 sm:bottom-[24%] sm:left-12">
        <p className="font-mono text-[10px] uppercase tracking-[0.5em] text-tech/50 sm:text-xs">
          MIT Tech Kernel
        </p>
        <p className="mt-3 font-mono text-[10px] uppercase tracking-[0.34em] text-ink/30 sm:text-[11px]">
          Engineers&rsquo; Day // 2026
        </p>
      </div>

      <button
        type="button"
        onClick={onSkip}
        className="pointer-events-auto absolute bottom-16 left-1/2 -translate-x-1/2 font-mono text-[10px] uppercase tracking-[0.2em] text-ink/30 transition-colors hover:text-signal"
      >
        Skip intro
      </button>
    </div>
  );
}

/**
 * The first district hand-off.
 *
 * Appears near the end of the drive. Events are places in the world, not cards:
 * the label reads as a road sign for a sector you are approaching, and the link is
 * the off-ramp into the practical site.
 */
function DistrictHandoff() {
  const events = useEvents();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const id = window.setInterval(() => setVisible(worldState.u > 0.62), 160);
    return () => window.clearInterval(id);
  }, []);

  const first = events.status === 'success' ? events.data[0] : undefined;
  if (first === undefined) return null;

  const identity = identityFor(first.eventId);

  return (
    <div
      className="pointer-events-none fixed inset-x-0 bottom-28 z-20 flex justify-center px-4 transition-all duration-700 sm:bottom-32"
      style={{
        opacity: visible ? 1 : 0,
        transform: visible ? 'translateY(0)' : 'translateY(24px)',
      }}
    >
      <div className="pointer-events-auto border border-ink/15 bg-void/70 px-5 py-4 backdrop-blur-md sm:px-7 sm:py-5">
        <p className="font-mono text-[9px] uppercase tracking-[0.3em] text-ink/40">
          Approaching sector
        </p>
        <p
          className="mt-2 font-display text-2xl leading-none sm:text-3xl"
          style={{ color: identity.accent }}
        >
          {identity.codename}
        </p>
        <p className="mt-2 font-mono text-[10px] uppercase tracking-[0.2em] text-ink/60">
          {first.name}
        </p>
        <Link
          to="/events"
          className="mt-4 inline-block border border-signal/50 px-4 py-2 font-mono text-[10px] uppercase tracking-[0.18em] text-signal transition-colors hover:bg-signal hover:text-void"
        >
          Enter the city →
        </Link>
      </div>
    </div>
  );
}

function DriveDocument() {
  return (
    <div className="sr-only">
      <h1>Engineers&rsquo; Day 2026 — MIT Tech Kernel</h1>
      <p>
        An interactive cinematic introduction is playing. It is decorative. All event
        information and registration is available without it.
      </p>
      <nav aria-label="Primary">
        <ul>
          <li>
            <Link to="/events">Browse all seven events</Link>
          </li>
          <li>
            <Link to="/my-registrations">My registrations</Link>
          </li>
        </ul>
      </nav>
    </div>
  );
}

function ContextLostFallback({ onContinue }: { onContinue: () => void }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-void px-6">
      <div className="max-w-md text-center">
        <p className="font-mono text-[10px] uppercase tracking-[0.3em] text-danger">
          Renderer stopped
        </p>
        <h1 className="mt-3 font-display text-2xl text-ink">The 3D view could not continue</h1>
        <p className="mt-3 text-sm text-muted">
          Your browser released the graphics context. Everything still works — the full event
          list and registration do not need it.
        </p>
        <Link
          to="/events"
          onClick={onContinue}
          className="clip-notch-sm mt-7 inline-block bg-signal px-6 py-3 font-mono text-xs uppercase tracking-[0.18em] font-semibold text-void"
        >
          Continue to events
        </Link>
      </div>
    </div>
  );
}
