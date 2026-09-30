import { useCallback, useEffect, useRef, useState } from 'react';
import {
  fetchProblem,
  fetchProblemBoard,
  runCode as runCodeRequest,
  saveDraft as saveDraftRequest,
  submitProblem as submitProblemRequest,
} from '@/services/arena/arenaApi';
import type {
  Difficulty,
  ProblemBoard,
  ProblemDetail,
  RunResult,
  SubmitResult,
} from '@/services/arena/arenaTypes';
import { ApiError } from '@/services/apiError';

/**
 * The debugging workspace: board, open problem, and autosave.
 *
 * Three behaviours worth calling out, because each solves a way a participant
 * could otherwise lose work or be misled:
 *
 * 1. **Drafts autosave on a debounce, and flush on the way out.** Typing schedules
 *    a save 1.2s later; switching problems, blurring, or hiding the tab flushes
 *    immediately. Without the flush, the last few seconds of edits before someone
 *    clicks another problem would be lost — which is exactly when they are most
 *    likely to click away.
 *
 * 2. **Save failures are visible.** A silent autosave failure is worse than no
 *    autosave, because the participant believes their work is safe. `saveState`
 *    surfaces it.
 *
 * 3. **The board is refetched after a save that changed status**, so a tile flips
 *    from `○` to `◐` without a manual reload.
 *
 * The server owns every status. Nothing here decides a problem is attempted or
 * solved — it renders what comes back.
 */

const AUTOSAVE_DEBOUNCE_MS = 1_200;

export type SaveState = 'idle' | 'saving' | 'saved' | 'failed';

export interface WorkspaceState {
  board: ProblemBoard | null;
  zone: Difficulty;
  problem: ProblemDetail | null;
  /** The live document. Seeded from the draft, or the bank's buggy code. */
  code: string;
  loadingBoard: boolean;
  loadingProblem: boolean;
  saveState: SaveState;
  savedAt: string | null;
  error: ApiError | null;

  /** The last execution result for the open problem, or null. */
  result: RunResult | null;
  /** The last submission result for the open problem, or null. */
  submission: SubmitResult | null;
  executing: boolean;
  /** Set when the judge itself failed. Distinct from a wrong answer. */
  executionOutage: string | null;

  selectZone: (zone: Difficulty) => void;
  openProblem: (ref: string) => void;
  editCode: (next: string) => void;
  /** Sends any pending draft now. Awaitable: the submit flow depends on it. */
  flush: () => Promise<void>;
  run: () => void;
  submit: () => void;
}

export function useWorkspace(token: string | null): WorkspaceState {
  const [board, setBoard] = useState<ProblemBoard | null>(null);
  const [zone, setZone] = useState<Difficulty>('EASY');
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [code, setCode] = useState('');
  const [loadingBoard, setLoadingBoard] = useState(true);
  const [loadingProblem, setLoadingProblem] = useState(false);
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [result, setResult] = useState<RunResult | null>(null);
  const [submission, setSubmission] = useState<SubmitResult | null>(null);
  const [executing, setExecuting] = useState(false);
  const [executionOutage, setExecutionOutage] = useState<string | null>(null);

  // Everything the debounced save needs, without making it a dependency of the
  // timer effect — a save scheduled for the problem you just left must still know
  // which problem it belongs to.
  const pending = useRef<{ ref: string; code: string; revision: number } | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const refreshBoard = useCallback(
    async (signal?: AbortSignal) => {
      if (token === null) return;
      try {
        setBoard(await fetchProblemBoard(token, signal));
      } catch (cause) {
        if (signal?.aborted) return;
        if (cause instanceof ApiError) setError(cause);
      } finally {
        if (signal?.aborted !== true) setLoadingBoard(false);
      }
    },
    [token],
  );

  /** Send whatever is pending, now. Used on problem switch, blur and tab-hide. */
  const flush = useCallback(async () => {
    if (token === null) return;
    const outstanding = pending.current;
    if (outstanding === null) return;

    pending.current = null;
    window.clearTimeout(timer.current);
    setSaveState('saving');

    try {
      const result = await saveDraftRequest(
        token,
        outstanding.ref,
        outstanding.code,
        outstanding.revision,
      );
      setSaveState('saved');
      setSavedAt(result.savedAt);

      // Keep the revision current so the next save is not rejected as stale.
      setProblem((current) =>
        current !== null && current.ref === result.ref
          ? { ...current, draftRevision: result.revision, status: result.status }
          : current,
      );
      void refreshBoard();
    } catch (cause) {
      setSaveState('failed');
      if (cause instanceof ApiError) setError(cause);
    }
  }, [token, refreshBoard]);

  // Initial board.
  useEffect(() => {
    if (token === null) return;
    const controller = new AbortController();
    // Synchronising with an external system — every state write inside
    // refreshBoard happens after the request resolves, not during this render.
    // oxlint-disable-next-line react/set-state-in-effect
    void refreshBoard(controller.signal);
    return () => controller.abort();
  }, [token, refreshBoard]);

  const openProblem = useCallback(
    (ref: string) => {
      if (token === null) return;

      // Anything typed on the previous problem goes now, not on a timer that this
      // navigation is about to outlive.
      void flush();

      setLoadingProblem(true);
      void (async () => {
        try {
          const detail = await fetchProblem(token, ref);
          setProblem(detail);
          // The draft when there is one, the bank's buggy code when there is not.
          setCode(detail.draftCode ?? detail.buggyCode);
          setSaveState('idle');
          setError(null);

          // Results belong to the problem that produced them. Carrying them across
          // a navigation would show one problem's verdict under another's title.
          setResult(null);
          setSubmission(null);
          setExecutionOutage(null);
        } catch (cause) {
          if (cause instanceof ApiError) setError(cause);
        } finally {
          setLoadingProblem(false);
        }
      })();
    },
    [token, flush],
  );

  /** Record a keystroke and (re)arm the debounce. */
  const editCode = useCallback(
    (next: string) => {
      setCode(next);
      if (problem === null) return;

      pending.current = {
        ref: problem.ref,
        code: next,
        revision: problem.draftRevision,
      };
      setSaveState('saving');

      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => void flush(), AUTOSAVE_DEBOUNCE_MS);
    },
    [problem, flush],
  );

  // Never lose the last edits to a closing tab or a machine going to sleep.
  useEffect(() => {
    const onHide = () => {
      if (document.hidden) void flush();
    };
    document.addEventListener('visibilitychange', onHide);
    window.addEventListener('pagehide', flush);
    return () => {
      document.removeEventListener('visibilitychange', onHide);
      window.removeEventListener('pagehide', flush);
      window.clearTimeout(timer.current);
    };
  }, [flush]);

  /**
   * Run or submit.
   *
   * <p>The pending draft is flushed first, so what is judged is what the server has
   * on record. The source itself is sent with the request rather than relied on from
   * the draft, so a run reflects the editor even if the save is still in flight.
   *
   * <p>An outage is held separately from `error`. "The judge is down, try again" and
   * "your code is wrong" are different things to put in front of someone, and the UI
   * must not render one as the other.
   */
  const execute = useCallback(
    async (kind: 'run' | 'submit') => {
      if (token === null || problem === null) return;

      await flush();
      setExecuting(true);
      setExecutionOutage(null);
      setError(null);

      try {
        if (kind === 'run') {
          setResult(await runCodeRequest(token, problem.ref, code));
        } else {
          const outcome = await submitProblemRequest(token, problem.ref, code);
          setSubmission(outcome);
          // The problem is now locked and its status has moved; both the panel and
          // the board need to reflect that without a manual reload.
          setProblem((current) =>
            current !== null && current.ref === outcome.ref
              ? { ...current, status: outcome.problemStatus }
              : current,
          );
          void refreshBoard();
        }
      } catch (cause) {
        const failure =
          cause instanceof ApiError ? cause : new ApiError('UNKNOWN', 'Execution failed.');

        if (failure.code === 'EXECUTION_UNAVAILABLE' || failure.code === 'NETWORK'
            || failure.code === 'UNAVAILABLE') {
          setExecutionOutage(failure.message);
        } else {
          setError(failure);
        }
      } finally {
        setExecuting(false);
      }
    },
    [token, problem, code, flush, refreshBoard],
  );

  return {
    board,
    zone,
    problem,
    code,
    loadingBoard,
    loadingProblem,
    saveState,
    savedAt,
    error,
    result,
    submission,
    executing,
    executionOutage,
    selectZone: setZone,
    openProblem,
    editCode,
    flush,
    run: () => void execute('run'),
    submit: () => void execute('submit'),
  };
}
