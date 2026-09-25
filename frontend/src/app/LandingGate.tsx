import { Suspense, lazy, useEffect, useState } from 'react';
import { PracticalShell } from './PracticalShell';
import { LandingPage } from '@/pages/LandingPage';
import { detectCapability, readOverride, type Capability } from '@/cinematic/capability';

/**
 * Decides what the front door is.
 *
 * Capable desktop with motion allowed → the cinematic drive.
 * Everything else → the practical landing page, immediately.
 *
 * "Everything else" is not a failure case. Reduced motion, no WebGL, a phone, a
 * weak GPU and an explicit opt-out all arrive here, and all of them get a complete,
 * fast, registerable site. This is the dependency arrow made literal: the gate
 * imports the cinematic layer lazily and can simply never call it.
 *
 * The decision is made once and held for the session, so a resize or a re-render
 * can never swap someone between the two mid-visit.
 */
const DriveRoute = lazy(() =>
  import('@/cinematic/DriveRoute').then(async (module) => {
    // Start fetching the 7.3 MB vehicle while the gate is still black, so the
    // model is resident by the time SCENE 02 needs to reveal it.
    const { preloadVehicle } = await import('@/cinematic/car/VehicleModel');
    preloadVehicle();
    return { default: module.DriveRoute };
  }),
);

export function LandingGate() {
  const [capability, setCapability] = useState<Capability | null>(null);

  useEffect(() => {
    const override = readOverride();
    const detected = detectCapability();

    setCapability(
      override === 'PRACTICAL' ? { ...detected, tier: 'PRACTICAL', reason: 'User preference' } : detected,
    );
  }, []);

  // One frame of black while detection runs. Deliberately black rather than a
  // spinner: if the drive is coming, this is already scene one.
  if (capability === null) {
    return <div className="min-h-dvh bg-[#04050a]" />;
  }

  if (capability.tier === 'PRACTICAL') {
    return (
      <PracticalShell>
        <LandingPage />
      </PracticalShell>
    );
  }

  return (
    <Suspense fallback={<div className="min-h-dvh bg-[#04050a]" />}>
      <DriveRoute tier={capability.tier} />
    </Suspense>
  );
}
