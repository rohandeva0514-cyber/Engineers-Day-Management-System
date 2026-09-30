import { Link } from 'react-router-dom';
import { useArenaStatus } from '@/hooks/arena/useArenaStatus';
import { useArenaSession } from '@/hooks/arena/useArenaSession';
import { ArenaFrame, ArenaLogLine, ArenaStatusLine } from './components/ArenaFrame';
import { AccessCodeScreen } from './screens/AccessCodeScreen';
import { IdentityScreen } from './screens/IdentityScreen';
import { LanguageSelectScreen } from './screens/LanguageSelectScreen';
import { MissionBriefingScreen } from './screens/MissionBriefingScreen';
import { MissionConsole } from './screens/MissionConsole';
import { AttemptClosedScreen } from './screens/AttemptClosedScreen';
import '@/styles/arena.css';

/**
 * The Debugging Arena.
 *
 * Outside the practical shell and outside the experience route, like `/admin`: it
 * owns the whole viewport, carries no site chrome, and — importantly — no Lenis.
 * Smooth-scroll hijacking is actively harmful once there is a code editor on the
 * screen, so the arena must never inherit it.
 *
 * ONE ROUTE, NO SUB-PATHS. The server owns which phase a participant is in; a URL
 * is a client-supplied claim about phase. Keeping the arena at a single address
 * means browser Back cannot produce a screen the server disagrees with, and a
 * bookmarked deep link cannot appear to skip a step.
 *
 * TWO SOURCES OF TRUTH, AND BOTH ARE THE SERVER'S. `useArenaStatus` polls the arena
 * switch so an organiser stopping the event reaches a participant within ten
 * seconds; `useArenaSession` holds the attempt, which is the authority on phase,
 * language lock and deadline. Neither is computed here.
 *
 * PHASE B ends at the mission handoff. The problem board and editor are Phase C.
 */
export function ArenaRoute() {
  const arena = useArenaStatus();
  const session = useArenaSession();

  // Hold still rather than flashing the access screen at someone who is already
  // mid-mission and simply pressed refresh.
  if (arena.loading || session.restoring) {
    return <ConnectingScreen />;
  }

  if (arena.unreachable || arena.snapshot === null) {
    return <NoSignalScreen onRetry={arena.refresh} />;
  }

  const attempt = session.session;

  // A finished attempt outranks everything, including the arena being closed. It is
  // the most specific true thing, and a student who submitted should see that they
  // submitted rather than a generic "arena over" notice.
  if (attempt !== null && isFinal(attempt.attempt.state)) {
    return <AttemptClosedScreen session={attempt} />;
  }

  // The arena switch comes next: an organiser closing the event must reach a
  // participant even when they are mid-flow.
  if (arena.snapshot.status !== 'ACTIVE') {
    return (
      <ClosedGate
        ended={arena.snapshot.status === 'ENDED'}
        minutes={Math.round(arena.snapshot.durationSeconds / 60)}
      />
    );
  }

  if (attempt === null) {
    return (
      <AccessCodeScreen
        onSubmit={(code) => void session.checkIn(code)}
        busy={session.busy}
        error={session.error}
      />
    );
  }

  if (attempt.attempt.state === 'ACTIVE' && session.token !== null) {
    return (
      <MissionConsole
        session={attempt}
        token={session.token}
        submitting={session.busy}
        submitError={session.error}
        onSubmitMission={() => void session.submitMission()}
      />
    );
  }

  // INITIALIZED: checked in, clock not started. Which of the three pre-mission
  // screens to show is the one thing the client decides, because the server does
  // not track whether someone is reading their identity card or the briefing.
  if (session.step === 'IDENTITY') {
    return (
      <IdentityScreen
        participant={attempt.participant}
        onContinue={() => session.goToStep('LANGUAGE')}
      />
    );
  }

  if (session.step === 'LANGUAGE') {
    return (
      <LanguageSelectScreen
        languages={session.languages}
        selected={attempt.attempt.language}
        onSelect={(language) => void session.selectLanguage(language)}
        onContinue={() => session.goToStep('BRIEFING')}
        onBack={() => session.goToStep('IDENTITY')}
        busy={session.busy}
        error={session.error}
      />
    );
  }

  return (
    <MissionBriefingScreen
      language={
        session.languages.find((option) => option.id === attempt.attempt.language) ?? null
      }
      durationSeconds={arena.snapshot.durationSeconds}
      onStart={() => void session.startMission()}
      onBack={() => session.goToStep('LANGUAGE')}
      busy={session.busy}
      error={session.error}
    />
  );
}

function isFinal(state: string): boolean {
  return state === 'SUBMITTED' || state === 'EXPIRED' || state === 'TERMINATED';
}

function ConnectingScreen() {
  return (
    <ArenaFrame tone="offline">
      <ArenaStatusLine tone="offline" label="Connecting" />
      <h1 className="arena__title">Debugging Arena</h1>
      <p className="arena__subtitle">Engineers&rsquo; Day 2026</p>
      <div className="arena__scan" aria-hidden="true" />
      <div className="arena__log">
        <ArenaLogLine label="System" value="Contacting mission control…" />
      </div>
    </ArenaFrame>
  );
}

/**
 * Nothing answered, and there is no earlier snapshot to keep showing.
 *
 * Said plainly rather than dressed up: a student staring at this needs to know it
 * is the network, not their code, and that someone should be told.
 */
function NoSignalScreen({ onRetry }: { onRetry: () => void }) {
  return (
    <ArenaFrame tone="error">
      <ArenaStatusLine tone="error" label="No signal" />
      <h1 className="arena__title">Debugging Arena</h1>
      <p className="arena__subtitle">Engineers&rsquo; Day 2026</p>
      <p className="arena__body">
        The arena service could not be reached. This is a connection problem, not
        something you have done. Check the network, then try again — and tell an
        event organiser if it keeps happening.
      </p>
      <div className="arena__actions">
        <button type="button" className="arena__button" onClick={onRetry}>
          Retry connection
        </button>
      </div>
    </ArenaFrame>
  );
}

/**
 * The waiting screen — the arena has not been started, or was paused.
 *
 * Deliberately renders no access-code field. A disabled input invites people to
 * type into it and then report that it is broken; an absent one says the door is
 * simply not open yet.
 *
 * It does not need a refresh button either. The status is polled, so this screen
 * replaces itself within ten seconds of an organiser pressing Start, and nobody has
 * to tell a hall full of students to reload.
 */
function ClosedGate({ ended, minutes }: { ended: boolean; minutes: number }) {
  if (ended) {
    return (
      <ArenaFrame tone="ended">
        <ArenaStatusLine tone="ended" label="Arena closed" />
        <h1 className="arena__title">Debugging Arena</h1>
        <p className="arena__subtitle">Engineers&rsquo; Day 2026</p>
        <p className="arena__body">
          Mission control has ended the arena. Submissions are closed and results are
          published by the organisers after evaluation.
        </p>
        <div className="arena__log">
          <ArenaLogLine label="Status" value="Ended" />
          <ArenaLogLine label="Check-in" value="Closed" />
        </div>
      </ArenaFrame>
    );
  }

  return (
    <ArenaFrame tone="offline">
      <ArenaStatusLine tone="offline" label="Offline" />
      <h1 className="arena__title">Debugging Arena</h1>
      <p className="arena__subtitle">Engineers&rsquo; Day 2026</p>

      <p className="arena__body">
        The arena has not started yet. Mission control will open it when the event
        begins, and this screen will change on its own — you do not need to refresh.
      </p>
      <p className="arena__body">
        Have your <strong>8-character access code</strong> ready. It was issued when
        you registered for Debugging and is listed under{' '}
        <Link to="/my-registrations" className="arena__link">
          My Registrations
        </Link>
        .
      </p>

      <div className="arena__scan" aria-hidden="true" />

      <div className="arena__log">
        <ArenaLogLine label="Status" value="Standby" />
        <ArenaLogLine label="Mission length" value={`${minutes} minutes`} />
        <ArenaLogLine label="System" value="Waiting for mission control" />
      </div>
    </ArenaFrame>
  );
}
