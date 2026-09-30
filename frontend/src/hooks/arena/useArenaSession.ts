import { useCallback, useEffect, useState } from 'react';
import {
  checkIn as checkInRequest,
  chooseLanguage as chooseLanguageRequest,
  fetchAttemptState,
  startMission as startMissionRequest,
  submitMission as submitMissionRequest,
} from '@/services/arena/arenaApi';
import {
  clearSessionToken,
  readSessionToken,
  writeSessionToken,
} from '@/services/arena/arenaSession';
import type {
  AttemptStateResponse,
  ArenaLanguageOption,
} from '@/services/arena/arenaTypes';
import { ApiError } from '@/services/apiError';

/**
 * The participant's journey through the arena, as one piece of state.
 *
 * The rule this hook exists to enforce: **the server owns the phase.** Nothing here
 * decides that a mission has started, that a language is locked, or that an attempt
 * is over — each of those is read from `AttemptStateResponse` and rendered. The
 * only thing the client decides is which of the three *pre-mission* screens to show
 * while the attempt sits in INITIALIZED, because the server does not track whether
 * someone is looking at their identity card or the briefing.
 *
 * On mount, if a token survives in `sessionStorage`, one `GET /arena/attempt`
 * rebuilds everything. A refresh mid-mission therefore returns to the mission with
 * the same deadline, and a refresh after submitting returns to the final screen —
 * without ever creating a second attempt, because the backend's uniqueness
 * constraint makes that impossible regardless of what the client does.
 */

/** Which pre-mission screen to show. Client-side only; meaningless once ACTIVE. */
export type PreMissionStep = 'IDENTITY' | 'LANGUAGE' | 'BRIEFING';

export interface ArenaSessionState {
  /** Null until a successful check-in. */
  session: AttemptStateResponse | null;
  /**
   * The bearer for workspace requests.
   *
   * Exposed so the console can fetch the board and save drafts. It is not a
   * capability the UI holds on its own — every request it appears on is
   * re-validated against a stored hash, and a rejected one drops the session.
   */
  token: string | null;
  languages: ArenaLanguageOption[];
  step: PreMissionStep;
  /** True while the very first rehydrate is in flight, so the UI can hold still. */
  restoring: boolean;
  busy: boolean;
  error: ApiError | null;

  checkIn: (code: string) => Promise<void>;
  selectLanguage: (language: string) => Promise<void>;
  startMission: () => Promise<void>;
  /** Final mission submission. Irreversible; the caller must flush drafts first. */
  submitMission: () => Promise<void>;
  goToStep: (step: PreMissionStep) => void;
  dismissError: () => void;
  signOut: () => void;
}

export function useArenaSession(): ArenaSessionState {
  const [token, setToken] = useState<string | null>(readSessionToken);
  const [session, setSession] = useState<AttemptStateResponse | null>(null);
  const [step, setStep] = useState<PreMissionStep>('IDENTITY');
  const [restoring, setRestoring] = useState(() => readSessionToken() !== null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  /** Drop a token the server has stopped honouring, and return to the gate. */
  const forget = useCallback(() => {
    clearSessionToken();
    setToken(null);
    setSession(null);
    setStep('IDENTITY');
  }, []);

  /**
   * Rehydrate from a stored token.
   *
   * A rejected session is not an error worth showing: it means the tab sat idle
   * past the window, or another device checked in. Either way the honest response
   * is the access screen, not a red banner.
   */
  useEffect(() => {
    // `restoring` already starts false when there was no stored token, so there is
    // nothing to write here — and writing it would be a synchronous state change
    // during the effect for no reason.
    if (token === null) return;

    const controller = new AbortController();

    void (async () => {
      try {
        const state = await fetchAttemptState(token, controller.signal);
        setSession(state);
        setStep(state.attempt.language === null ? 'IDENTITY' : 'LANGUAGE');
      } catch {
        if (controller.signal.aborted) return;
        forget();
      } finally {
        if (!controller.signal.aborted) setRestoring(false);
      }
    })();

    return () => controller.abort();
  }, [token, forget]);

  /** Run a request, mapping every failure into one ApiError the UI can branch on. */
  const run = useCallback(
    async (request: () => Promise<AttemptStateResponse>): Promise<void> => {
      setBusy(true);
      setError(null);
      try {
        setSession(await request());
      } catch (cause) {
        const failure =
          cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'That did not work.');

        // The session is gone — expired, or displaced by another device. Send them
        // back to the access screen rather than leaving a dead page on screen.
        if (failure.code === 'ARENA_SESSION_INVALID') {
          forget();
        }

        // Recorded, not rethrown. Callers fire these from click handlers and have
        // nothing useful to do with a rejection; rethrowing would only produce an
        // unhandled promise rejection behind an error the UI is already showing.
        setError(failure);
      } finally {
        setBusy(false);
      }
    },
    [forget],
  );

  const checkIn = useCallback(
    async (code: string) => {
      setBusy(true);
      setError(null);
      try {
        const response = await checkInRequest(code);
        writeSessionToken(response.sessionToken);

        // Seed from the check-in response so the identity card renders immediately,
        // then let the token change trigger the authoritative rehydrate.
        setSession({
          serverTime: response.serverTime,
          durationSeconds: 0,
          arenaStatus: 'ACTIVE',
          participant: response.participant,
          attempt: response.attempt,
          languages: response.languages,
        });
        setStep(response.attempt.language === null ? 'IDENTITY' : 'LANGUAGE');
        setRestoring(false);
        setToken(response.sessionToken);
      } catch (cause) {
        setError(
          cause instanceof ApiError
            ? cause
            : new ApiError('UNKNOWN', 'Verification failed.'),
        );
      } finally {
        setBusy(false);
      }
    },
    [],
  );

  const selectLanguage = useCallback(
    async (language: string) => {
      if (token === null) return;
      await run(() => chooseLanguageRequest(token, language));
    },
    [token, run],
  );

  const startMission = useCallback(async () => {
    if (token === null) return;
    const language = session?.attempt.language;
    if (!language) return;
    await run(() => startMissionRequest(token, language));
  }, [token, session, run]);

  /**
   * Submit the mission.
   *
   * <p>Goes through the same `run` helper as every other mutation, so a rejected
   * session drops to the access screen and any refusal lands in `error` rather than
   * becoming an unhandled rejection. The response is the attempt's new state, which
   * flips the route to the mission-complete screen on its own — there is no local
   * "submitted" flag that could disagree with the server.
   */
  const submitMission = useCallback(async () => {
    if (token === null) return;
    await run(() => submitMissionRequest(token));
  }, [token, run]);

  return {
    session,
    token,
    languages: session?.languages ?? [],
    step,
    restoring,
    busy,
    error,
    checkIn,
    selectLanguage,
    startMission,
    submitMission,
    goToStep: setStep,
    dismissError: () => setError(null),
    signOut: forget,
  };
}
