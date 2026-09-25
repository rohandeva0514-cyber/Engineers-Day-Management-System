import { useEffect, useRef } from 'react';
import { ButtonLink } from '@/components/Button';
import { TechLabel } from '@/components/Panel';
import { StatusBadge } from '@/components/StatusBadge';
import { SITE, identityFor } from '@/data/siteContent';
import { useEvents } from '@/hooks/useEvents';
import { availabilityOf } from '@/domain/rules';
import { pad2 } from '@/lib/format';

/**
 * The opening interface.
 *
 * Restraint is the brief: this is not the cinematic sequence, and pretending
 * otherwise with a wall of glow would make the real thing an anticlimax. What it
 * does establish is the grammar the cinematic layer will inherit — the technical
 * labels, the angular geometry, the single amber signal against near-black, the
 * sense of reading an instrument rather than a landing page.
 *
 * The event index at the bottom is live API data, not decoration, so the page
 * proves the backend is reachable the moment it loads.
 */
export function LandingPage() {
  const titleRef = useRef<HTMLDivElement>(null);
  const events = useEvents();

  /**
   * One short staggered entrance.
   *
   * GSAP is imported dynamically so it never enters the entry chunk — the whole
   * library would otherwise be downloaded by someone whose only goal is to fill in
   * a registration form. It is also the library the cinematic layer will be built
   * on, so keeping it code-split from the start is the boundary working as intended.
   *
   * The page renders complete without it: the animation only ever moves elements
   * that are already visible, so a failed or skipped import costs nothing.
   */
  useEffect(() => {
    const root = titleRef.current;
    if (root === null) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let cleanup: (() => void) | undefined;
    let cancelled = false;

    void import('gsap').then(({ gsap }) => {
      if (cancelled) return;
      const timeline = gsap.timeline({ defaults: { ease: 'power3.out' } });
      timeline.fromTo(
        root.querySelectorAll('[data-boot]'),
        { opacity: 0, y: 14 },
        { opacity: 1, y: 0, duration: 0.55, stagger: 0.07 },
      );
      cleanup = () => timeline.kill();
    });

    return () => {
      cancelled = true;
      cleanup?.();
    };
  }, []);

  return (
    <>
      <section className="relative overflow-hidden border-b border-line">
        {/* A single wide glow low behind the title. One light source, not a gradient wash. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-40 left-1/2 h-80 w-[min(1100px,140vw)] -translate-x-1/2 rounded-[50%] bg-signal/10 blur-[110px]"
        />

        <div ref={titleRef} className="relative mx-auto max-w-7xl px-4 pt-16 pb-20 sm:px-6 sm:pt-24 sm:pb-28">
          <div data-boot className="flex items-center gap-3">
            <span className="h-px w-8 bg-signal" aria-hidden="true" />
            <TechLabel bright>{SITE.org}</TechLabel>
          </div>

          <h1 className="mt-6 max-w-4xl">
            <span
              data-boot
              className="block font-display text-[clamp(2.6rem,9vw,6.5rem)] font-bold leading-[0.92] tracking-[-0.02em] text-ink text-glow"
            >
              ENGINEERS&rsquo;
            </span>
            <span
              data-boot
              className="mt-1 block font-display text-[clamp(2.6rem,9vw,6.5rem)] font-bold leading-[0.92] tracking-[-0.02em] text-signal text-glow"
            >
              DAY 2026
            </span>
          </h1>

          <p data-boot className="mt-7 max-w-xl text-base leading-relaxed text-muted sm:text-lg">
            {SITE.intro}
          </p>

          <div data-boot className="mt-10 flex flex-wrap gap-3">
            <ButtonLink to="/events" size="lg">
              Explore Events
            </ButtonLink>
            <ButtonLink to="/events" variant="secondary" size="lg">
              Register
            </ButtonLink>
          </div>

          <dl
            data-boot
            className="mt-14 grid max-w-2xl grid-cols-2 gap-px border border-line bg-line sm:grid-cols-4"
          >
            <Stat label="Events" value="07" />
            <Stat label="Years" value="1 & 2" />
            <Stat label="Deadline" value={SITE.registrationDeadlineLabel} />
            <Stat label="Entry" value="FREE" />
          </dl>
        </div>
      </section>

      {/* Live event index — proves the API is up and gives a real route onward. */}
      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <div className="flex items-end justify-between gap-6 border-b border-line pb-4">
          <div>
            <TechLabel className="mb-2">Programme</TechLabel>
            <h2 className="text-2xl text-ink sm:text-3xl">The seven events</h2>
          </div>
          <ButtonLink to="/events" variant="ghost" className="shrink-0">
            View all
          </ButtonLink>
        </div>

        {events.status === 'success' && (
          <ul className="mt-2">
            {events.data.map((event, index) => {
              const identity = identityFor(event.eventId);
              return (
                <li key={event.eventId}>
                  <a
                    href={`/events/${event.eventId}`}
                    className="group flex items-center gap-4 border-b border-line py-4 transition-colors hover:bg-panel/60 sm:gap-6"
                  >
                    <span className="label-tech w-6 shrink-0" data-tabular>
                      {pad2(index + 1)}
                    </span>
                    <span
                      aria-hidden="true"
                      className="h-8 w-[3px] shrink-0 transition-all group-hover:h-10"
                      style={{ backgroundColor: identity.accent }}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-display text-lg text-ink transition-colors group-hover:text-signal">
                        {event.name}
                      </span>
                      <span className="label-tech mt-0.5 block truncate">{identity.kicker}</span>
                    </span>
                    <StatusBadge status={availabilityOf(event)} className="shrink-0" />
                  </a>
                </li>
              );
            })}
          </ul>
        )}

        {events.status === 'loading' && (
          <p className="label-tech py-10">Loading programme…</p>
        )}

        {events.status === 'error' && (
          <p className="py-10 text-sm text-muted">
            The programme could not be loaded right now.{' '}
            <a href="/events" className="text-signal underline underline-offset-4">
              Open the events page
            </a>{' '}
            to try again.
          </p>
        )}
      </section>
    </>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-panel px-4 py-3.5">
      <dt className="label-tech">{label}</dt>
      <dd className="mt-1 font-display text-lg text-ink" data-tabular>
        {value}
      </dd>
    </div>
  );
}
