import { useEffect, useRef, useState } from 'react';
import { useWorkspace } from '@/hooks/arena/useWorkspace';
import { useMissionClock } from '@/hooks/arena/useMissionClock';
import { CodeEditor } from '../components/CodeEditor';
import type { AttemptStateResponse, Difficulty, ProblemStatus } from '@/services/arena/arenaTypes';
import type { ApiError } from '@/services/apiError';
import '@/styles/console.css';

/**
 * The debugging console.
 *
 * Header, board, problem, editor, and one irreversible action.
 *
 * MANUAL-EVALUATION MODE. There is no Run button and no test output: live execution
 * is not part of this event, and the organisers read the saved code instead. Nothing
 * here shows a verdict, a score, or anything about the judge — a participant should
 * not be told about infrastructure that is deliberately out of the loop.
 *
 * All three zones are open from the first render. There is no unlock, no progression
 * and no ordering: a participant may work Hard first and Easy last, and the board
 * reflects that rather than resisting it.
 */
export function MissionConsole({
  session,
  token,
  onSubmitMission,
  submitting,
  submitError,
}: {
  session: AttemptStateResponse;
  token: string;
  /** Flushes pending drafts, then submits. Owned by the route, not this screen. */
  onSubmitMission: () => void;
  submitting: boolean;
  /**
   * Why the last submission attempt failed, if it did.
   *
   * Comes from the session rather than the workspace: submitting is an attempt-level
   * action, so its failures land in a different place from a draft save's. Rendering
   * only the workspace's error is what made a failed submission look like nothing
   * happening at all.
   */
  submitError: ApiError | null;
}) {
  const workspace = useWorkspace(token);
  const clock = useMissionClock(session.attempt.expiresAt, session.serverTime);

  const zones = workspace.board?.zones ?? [];
  const current = zones.find((z) => z.difficulty === workspace.zone);

  // Open the first problem of the board once it arrives, so the console never
  // renders an empty right-hand side waiting for a click.
  useEffect(() => {
    if (workspace.problem !== null || workspace.board === null) return;
    const first = workspace.board.zones.flatMap((z) => z.problems)[0];
    if (first !== undefined) workspace.openProblem(first.ref);
    // Intentionally keyed on board arrival only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspace.board]);

  return (
    <div className="console">
      <ConsoleHeader session={session} clock={clock} workspace={workspace} />

      <nav className="console__zones" aria-label="Difficulty">
        {zones.map((z) => (
          <button
            key={z.difficulty}
            type="button"
            className="console__zone"
            data-active={workspace.zone === z.difficulty}
            aria-current={workspace.zone === z.difficulty}
            onClick={() => workspace.selectZone(z.difficulty)}
          >
            <span>{z.difficulty}</span>
            <span className="console__zone-strip" aria-hidden="true">
              {z.problems.map((p) => (
                <i key={p.ref} data-status={p.status} />
              ))}
            </span>
          </button>
        ))}
      </nav>

      <div className="console__rail" role="tablist" aria-label="Problems">
        {(current?.problems ?? []).map((p) => (
          <button
            key={p.ref}
            type="button"
            role="tab"
            aria-selected={workspace.problem?.ref === p.ref}
            className="console__chip"
            data-active={workspace.problem?.ref === p.ref}
            data-status={p.status}
            onClick={() => workspace.openProblem(p.ref)}
          >
            <span className="console__chip-mark" aria-hidden="true">
              {mark(p.status)}
            </span>
            <span className="console__chip-ref">{p.ref}</span>
            <span className="console__chip-title">{p.title}</span>
            <span className="console__chip-points">{p.points}</span>
          </button>
        ))}
      </div>

      <div className="console__body">
        <section className="console__brief" aria-label="Debugging target">
          {workspace.problem === null ? (
            <p className="console__muted">
              {workspace.loadingBoard ? 'Loading board…' : 'Select a problem.'}
            </p>
          ) : (
            <>
              <p className="console__label">Debugging target</p>
              <h2 className="console__brief-title">
                {workspace.problem.ref} · {workspace.problem.title}
              </h2>
              <p className="console__meta">
                {workspace.problem.difficulty} · {workspace.problem.points} points
                {workspace.problem.concepts.length > 0 &&
                  ` · ${workspace.problem.concepts.join(', ')}`}
              </p>

              <p className="console__statement">{workspace.problem.problemStatement}</p>

              <Section label="Input">{workspace.problem.inputFormat}</Section>
              <Section label="Output">{workspace.problem.outputFormat}</Section>
              <Section label="Constraints">{workspace.problem.constraints}</Section>

              <p className="console__label console__label--spaced">Examples</p>
              {workspace.problem.visibleTests.map((test, i) => (
                // Examples are positional and never reorder.
                // eslint-disable-next-line react/no-array-index-key
                <div className="console__example" key={i}>
                  <div>
                    <span>in</span>
                    <pre>{test.input}</pre>
                  </div>
                  <div>
                    <span>out</span>
                    <pre>{test.expectedOutput}</pre>
                  </div>
                </div>
              ))}
            </>
          )}
        </section>

        <section className="console__terminal" aria-label="Code editor">
          <div className="console__terminal-bar">
            <span className="console__label">
              {workspace.problem === null ? 'Editor' : `${workspace.problem.ref} · ${session.attempt.language}`}
            </span>
            <span className="console__save" data-state={workspace.saveState}>
              {saveLabel(workspace.saveState)}
            </span>
          </div>

          {workspace.problem === null ? (
            <div className="console__editor-empty" />
          ) : (
            <CodeEditor
              // Remounts on problem change, which is how the next document is
              // loaded — CodeMirror owns its buffer and is not a controlled input.
              key={workspace.problem.ref}
              value={workspace.code}
              language={session.attempt.language ?? 'cpp'}
              onChange={workspace.editCode}
            />
          )}
        </section>
      </div>

      <SubmitDock
        workspace={workspace}
        onSubmit={onSubmitMission}
        busy={submitting}
        submitError={submitError}
      />
    </div>
  );
}

/**
 * The bottom dock, in manual-evaluation mode.
 *
 * Live execution is not part of this event, so there is no Run button and no test
 * output — a control that cannot judge anything is worse than no control, and an
 * "Execution service unavailable" message would be telling participants about
 * infrastructure that is deliberately not in the loop.
 *
 * What is left is the two things that matter: confirmation that their work is saved,
 * and the one irreversible action.
 */
function SubmitDock({
  workspace,
  onSubmit,
  busy,
  submitError,
}: {
  workspace: ReturnType<typeof useWorkspace>;
  onSubmit: () => void;
  busy: boolean;
  submitError: ApiError | null;
}) {
  const [confirming, setConfirming] = useState(false);

  return (
    <div className="console__dock">
      <div className="console__dock-output">
        <p className="console__label">Mission status</p>
        <p className="console__muted">
          Your code is saved automatically as you type. When you are finished, submit
          your mission — the organisers review your saved code for every problem.
        </p>
        {submitError !== null && (
          <p className="console__muted" data-tone="fail" role="alert">
            {submitError.message}
          </p>
        )}
        {workspace.error !== null && (
          <p className="console__muted" data-tone="fail" role="alert">
            {workspace.error.message}
          </p>
        )}
      </div>

      <div className="console__dock-actions">
        <span className="console__save" data-state={workspace.saveState}>
          {workspace.saveState === 'failed'
            ? 'Not saved — retrying'
            : workspace.saveState === 'saving'
              ? 'Saving…'
              : 'All work saved'}
        </span>
        <button
          type="button"
          className="console__button console__button--primary"
          disabled={busy}
          onClick={() => setConfirming(true)}
        >
          {busy ? 'Submitting…' : 'Submit mission'}
        </button>
      </div>

      {confirming && (
        <ConfirmSubmit
          busy={busy}
          submitError={submitError}
          onCancel={() => setConfirming(false)}
          onConfirm={() => {
            // Flush the pending draft and WAIT for it before submitting. The
            // backend submits whatever it has persisted, so without this a
            // participant who hits Submit within the autosave debounce would
            // submit up to 1.2 seconds behind what they can see on screen.
            void (async () => {
              await workspace.flush();
              onSubmit();
            })();
          }}
        />
      )}
    </div>
  );
}

/**
 * The one irreversible confirmation in the participant flow.
 *
 * Focus lands on Cancel and Escape dismisses, because the destructive option should
 * never be the one a stray keypress takes. It states plainly what is submitted — the
 * saved code — so nobody is surprised that an unsaved keystroke did not count.
 */
function ConfirmSubmit({
  busy,
  submitError,
  onCancel,
  onConfirm,
}: {
  busy: boolean;
  submitError: ApiError | null;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const cancel = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    cancel.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onCancel();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onCancel]);

  return (
    <div className="console__modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <div className="console__modal-panel">
        <p className="console__label">Confirm</p>
        <h2 id="confirm-title" className="console__modal-title">
          Submit your mission?
        </h2>
        <p className="console__modal-body">
          Your current saved code will be submitted for manual evaluation. You will
          not be able to edit your submission after this.
        </p>
        {/* Shown here, not only in the dock: the participant is looking at this
            dialog, and a failure that appeared behind it would read as the button
            doing nothing. The dialog deliberately stays open so the message sits
            next to the action that produced it. */}
        {submitError !== null && (
          <p className="console__modal-error" role="alert">
            {submitError.message}
          </p>
        )}

        <div className="console__modal-actions">
          <button
            type="button"
            ref={cancel}
            className="console__button"
            disabled={busy}
            onClick={onCancel}
          >
            Cancel
          </button>
          <button
            type="button"
            className="console__button console__button--primary"
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? 'Submitting…' : 'Confirm submission'}
          </button>
        </div>
      </div>
    </div>
  );
}

function ConsoleHeader({
  session,
  clock,
  workspace,
}: {
  session: AttemptStateResponse;
  clock: ReturnType<typeof useMissionClock>;
  workspace: ReturnType<typeof useWorkspace>;
}) {
  return (
    <header className="console__header">
      <div>
        <p className="console__label">Debugging Arena</p>
        <p className="console__muted">{session.participant.fullName}</p>
      </div>

      <div className="console__header-clock">
        <p className="console__label">Mission time</p>
        <p className="console__clock" data-urgency={clock.urgency} data-tabular>
          {clock.display}
        </p>
      </div>

      <div className="console__header-meta">
        <p className="console__label">
          <span className="console__dot" aria-hidden="true" /> Active ·{' '}
          {session.attempt.language}
        </p>
        <p className="console__muted">
          {workspace.board === null
            ? '—'
            : `${workspace.board.solvedCount} / ${workspace.board.totalProblems} solved`}
        </p>
      </div>
    </header>
  );
}

function Section({ label, children }: { label: string; children: string }) {
  return (
    <div className="console__section">
      <p className="console__label">{label}</p>
      <p className="console__section-body">{children}</p>
    </div>
  );
}

function mark(status: ProblemStatus): string {
  if (status === 'SOLVED') return '✓';
  if (status === 'ATTEMPTED') return '◐';
  return '○';
}

function saveLabel(state: ReturnType<typeof useWorkspace>['saveState']): string {
  switch (state) {
    case 'saving':
      return 'Saving…';
    case 'saved':
      return 'Saved';
    case 'failed':
      return 'Not saved — retrying';
    default:
      return '';
  }
}

export type { Difficulty };
