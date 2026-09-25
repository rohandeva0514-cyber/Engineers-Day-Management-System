import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useEvents } from '@/hooks/useEvents';
import { EventCard } from '@/features/events/EventCard';
import { ErrorState } from '@/components/ErrorState';
import { Spinner, SkeletonRow } from '@/components/Spinner';
import { TechLabel } from '@/components/Panel';
import { availabilityOf, isSolo, isYearEligible } from '@/domain/rules';
import type { Event, YearLevel } from '@/domain/types';
import { cn } from '@/lib/cn';

type YearFilter = 'ALL' | YearLevel;
type EntryFilter = 'ALL' | 'SOLO' | 'TEAM';

/**
 * Event discovery.
 *
 * Everything on this page comes from `GET /api/events`. The filters are pure
 * presentation over that data — they narrow what is shown, never what is true, and
 * an event filtered out is still fully reachable by URL.
 *
 * Filter state lives in the query string so a comparison view is shareable: sending
 * someone `/events?year=1` is a reasonable thing for a student to do.
 */
export function EventsPage() {
  const events = useEvents();
  const [searchParams, setSearchParams] = useSearchParams();
  const [entry, setEntry] = useState<EntryFilter>('ALL');

  const yearParam = searchParams.get('year');
  const year: YearFilter = yearParam === '1' ? 1 : yearParam === '2' ? 2 : 'ALL';

  const setYear = (next: YearFilter) => {
    const params = new URLSearchParams(searchParams);
    if (next === 'ALL') params.delete('year');
    else params.set('year', String(next));
    setSearchParams(params, { replace: true });
  };

  const visible = useMemo(() => {
    if (events.status !== 'success') return [];
    return events.data.filter((event: Event) => {
      if (year !== 'ALL' && !isYearEligible(event, year)) return false;
      if (entry === 'SOLO' && !isSolo(event)) return false;
      if (entry === 'TEAM' && isSolo(event)) return false;
      return true;
    });
  }, [events, year, entry]);

  const openCount =
    events.status === 'success'
      ? events.data.filter((event) => availabilityOf(event) === 'OPEN').length
      : 0;

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 sm:py-14">
      <header className="border-b border-line pb-6">
        <TechLabel bright className="mb-3">
          Programme / All events
        </TechLabel>
        <h1 className="text-3xl text-ink sm:text-4xl">Events</h1>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-muted sm:text-base">
          Seven events across both years. Check eligibility and team requirements before you
          register &mdash; entry rules are enforced when you submit.
        </p>
        {events.status === 'success' && (
          <p className="label-tech mt-4" data-tabular>
            {events.data.length} EVENTS &middot;{' '}
            <span className="text-ok">{openCount} OPEN</span>
          </p>
        )}
      </header>

      {/* Filters. Two radio groups, both keyboard-operable, neither hiding anything
          permanently — the URL always reaches every event. */}
      <div className="flex flex-col gap-4 border-b border-line py-5 sm:flex-row sm:items-center sm:gap-8">
        <FilterGroup
          legend="Year"
          options={[
            { value: 'ALL', label: 'All' },
            { value: 1, label: '1st year' },
            { value: 2, label: '2nd year' },
          ]}
          selected={year}
          onSelect={setYear}
        />
        <FilterGroup
          legend="Entry"
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'SOLO', label: 'Solo' },
            { value: 'TEAM', label: 'Team' },
          ]}
          selected={entry}
          onSelect={setEntry}
        />
      </div>

      <div className="pt-8">
        {events.status === 'loading' && <LoadingGrid />}

        {events.status === 'error' && (
          <ErrorState
            error={events.error}
            onRetry={events.reload}
            backTo={{ to: '/', label: 'Back to home' }}
          />
        )}

        {events.status === 'success' && visible.length === 0 && (
          <div className="border border-line bg-panel px-6 py-16 text-center">
            <TechLabel className="mb-2">No matches</TechLabel>
            <p className="text-ink">No events match these filters.</p>
            <p className="mt-2 text-sm text-muted">Try clearing the year or entry filter.</p>
          </div>
        )}

        {events.status === 'success' && visible.length > 0 && (
          <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {visible.map((event, index) => (
              <li key={event.eventId} className="anim-rise" style={{ animationDelay: `${index * 40}ms` }}>
                <EventCard event={event} index={index} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

interface FilterOption<T> {
  value: T;
  label: string;
}

/**
 * A segmented filter built from real radio inputs.
 *
 * Buttons with `aria-pressed` would also work, but radios give arrow-key movement
 * within the group for free and announce as "1 of 3" — worth more than the styling
 * it costs to hide the native control.
 */
function FilterGroup<T extends string | number>({
  legend,
  options,
  selected,
  onSelect,
}: {
  legend: string;
  options: FilterOption<T>[];
  selected: T;
  onSelect: (value: T) => void;
}) {
  return (
    <fieldset className="min-w-0">
      <legend className="label-tech mb-2">{legend}</legend>
      <div className="flex flex-wrap gap-px bg-line">
        {options.map((option) => {
          const isSelected = option.value === selected;
          return (
            <label
              key={String(option.value)}
              className={cn(
                'cursor-pointer px-3.5 py-2 font-mono text-[11px] uppercase tracking-[0.12em] transition-colors',
                'focus-within:outline focus-within:outline-2 focus-within:outline-signal',
                isSelected ? 'bg-signal text-void' : 'bg-panel text-muted hover:bg-raised hover:text-ink',
              )}
            >
              <input
                type="radio"
                name={legend}
                className="sr-only"
                checked={isSelected}
                onChange={() => onSelect(option.value)}
              />
              {option.label}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

function LoadingGrid() {
  return (
    <>
      <Spinner label="Loading events" className="mb-6" />
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <li key={index} className="border border-line bg-panel p-5">
            <SkeletonRow className="h-3 w-24" />
            <SkeletonRow className="mt-4 h-6 w-40" />
            <SkeletonRow className="mt-3 h-3 w-full" />
            <SkeletonRow className="mt-2 h-3 w-4/5" />
            <SkeletonRow className="mt-6 h-10 w-full" />
          </li>
        ))}
      </ul>
    </>
  );
}
