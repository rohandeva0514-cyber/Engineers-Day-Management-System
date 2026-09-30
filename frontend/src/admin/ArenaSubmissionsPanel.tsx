import { useCallback, useEffect, useState } from 'react';
import {
  awardPoints,
  downloadSubmissionsCsv,
  fetchSubmission,
  fetchSubmissions,
  type SubmissionDetail,
  type SubmissionRow,
  type SubmittedProblem,
} from './adminApi';

/**
 * Manual evaluation, for organisers.
 *
 * Built for one job on one day: read a participant's code for each problem and award
 * 0 or full points. Density over polish, like the rest of this panel.
 *
 * Deliberately does not decide anything. It shows no verdict, computes no
 * correctness, and names no winner — the standings are ordered by score and then by
 * submission time, which is the documented tiebreak, and the last call is a person's.
 *
 * The export is the important control here. If anything about this screen misbehaves
 * during the event, the CSV still contains every submission and the whole thing can
 * be marked from a spreadsheet.
 */
export function ArenaSubmissionsPanel() {
  const [rows, setRows] = useState<SubmissionRow[] | null>(null);
  const [open, setOpen] = useState<SubmissionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setRows((await fetchSubmissions()).submissions);
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load submissions.');
    }
  }, []);

  useEffect(() => {
    // Synchronising with an external system — the state write happens after the
    // request resolves, not during this render pass.
    // oxlint-disable-next-line react/set-state-in-effect
    void load();
  }, [load]);

  async function openSubmission(attemptId: number) {
    setBusy(true);
    try {
      setOpen(await fetchSubmission(attemptId));
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load that submission.');
    } finally {
      setBusy(false);
    }
  }

  async function award(attemptId: number, ref: string, points: number | null) {
    setBusy(true);
    try {
      setOpen(await awardPoints(attemptId, ref, points));
      await load();
      setError(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'That award did not apply.');
    } finally {
      setBusy(false);
    }
  }

  async function exportCsv() {
    try {
      const url = await downloadSubmissionsCsv();
      const link = document.createElement('a');
      link.href = url;
      link.download = 'debugging-submissions.csv';
      link.click();
      URL.revokeObjectURL(url);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The export failed.');
    }
  }

  return (
    <section aria-labelledby="submissions-heading">
      <h2 id="submissions-heading" className="admin__section-title">
        Debugging submissions
      </h2>

      {error !== null && (
        <p className="admin__error" role="alert">
          {error}
        </p>
      )}

      <div className="admin__bar-actions" style={{ marginBottom: '0.75rem' }}>
        <button type="button" className="admin__button" onClick={() => void load()}>
          Refresh
        </button>
        <button
          type="button"
          className="admin__button admin__button--primary"
          onClick={() => void exportCsv()}
        >
          Export all as CSV
        </button>
      </div>

      {rows === null ? (
        <p className="admin__muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="admin__muted">No submissions yet.</p>
      ) : (
        <div className="admin__table-wrap">
          <table className="admin__table">
            <thead>
              <tr>
                <th scope="col">Participant</th>
                <th scope="col">Language</th>
                <th scope="col">Submitted</th>
                <th scope="col">Score</th>
                <th scope="col">Evaluation</th>
                <th scope="col">Code</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.attemptId}>
                  <th scope="row">
                    <span className="admin__event-name">{row.fullName}</span>
                    <span className="admin__muted admin__event-id">
                      {row.rollNo} · {row.branch ?? '—'} · Year {row.yearLevel} ·{' '}
                      {row.division ?? '—'}
                    </span>
                  </th>
                  <td>{row.language ?? '—'}</td>
                  <td>
                    {/* Absent means the clock ran out before they submitted. Their
                        code is still here and still has to be marked. */}
                    {row.finalSubmittedAt === null ? (
                      <span className="admin__muted">not submitted · {row.state}</span>
                    ) : (
                      new Date(row.finalSubmittedAt).toLocaleTimeString()
                    )}
                  </td>
                  <td data-tabular>{row.totalScore ?? '—'}</td>
                  <td>
                    <span className="admin__badge" data-tone={toneOf(row.evaluationStatus)}>
                      {row.evaluationStatus}
                    </span>
                    <span className="admin__muted admin__note">
                      {row.problemsEvaluated}/{row.problemsTotal} marked
                    </span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="admin__button"
                      disabled={busy}
                      onClick={() => void openSubmission(row.attemptId)}
                    >
                      Review
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {open !== null && (
        <SubmissionReview
          detail={open}
          busy={busy}
          onClose={() => setOpen(null)}
          onAward={(ref, points) => void award(open.participant.attemptId, ref, points)}
        />
      )}
    </section>
  );
}

/** One participant's twelve problems, with the code and the award controls. */
function SubmissionReview({
  detail,
  busy,
  onClose,
  onAward,
}: {
  detail: SubmissionDetail;
  busy: boolean;
  onClose: () => void;
  onAward: (ref: string, points: number | null) => void;
}) {
  const [shown, setShown] = useState<string | null>(null);
  const p = detail.participant;

  return (
    <section className="admin__master" style={{ display: 'block', marginTop: '1rem' }}>
      <div className="admin__bar-actions" style={{ float: 'right' }}>
        <button type="button" className="admin__button" onClick={onClose}>
          Close
        </button>
      </div>

      <p className="admin__label">Reviewing</p>
      <p className="admin__master-status">{p.fullName}</p>
      <p className="admin__muted">
        {p.rollNo} · {p.email} · {p.language ?? '—'} · total {p.totalScore ?? '—'}
      </p>

      <div className="admin__table-wrap" style={{ marginTop: '1rem' }}>
        <table className="admin__table">
          <thead>
            <tr>
              <th scope="col">Problem</th>
              <th scope="col">Difficulty</th>
              <th scope="col">Points</th>
              <th scope="col">Award</th>
              <th scope="col">Code</th>
            </tr>
          </thead>
          <tbody>
            {detail.problems.map((problem) => (
              <tr key={problem.ref}>
                <th scope="row">
                  <span className="admin__event-name">
                    {problem.ref} · {problem.title}
                  </span>
                  {!problem.attempted && (
                    <span className="admin__muted admin__note">not attempted</span>
                  )}
                </th>
                <td>{problem.difficulty}</td>
                <td data-tabular>{problem.points}</td>
                <td>
                  {/* Two buttons, not a number field. The rules allow exactly two
                      values, so offering a free-text score would only invite one
                      that the backend then refuses. */}
                  <button
                    type="button"
                    className="admin__button"
                    disabled={busy}
                    data-active={problem.awardedPoints === problem.points}
                    onClick={() => onAward(problem.ref, problem.points)}
                  >
                    {problem.points}
                  </button>
                  <button
                    type="button"
                    className="admin__button"
                    disabled={busy}
                    data-active={problem.awardedPoints === 0}
                    onClick={() => onAward(problem.ref, 0)}
                  >
                    0
                  </button>
                  {problem.awardedPoints !== null && (
                    <button
                      type="button"
                      className="admin__button"
                      disabled={busy}
                      onClick={() => onAward(problem.ref, null)}
                      title="Clear this award"
                    >
                      clear
                    </button>
                  )}
                </td>
                <td>
                  <button
                    type="button"
                    className="admin__button"
                    onClick={() => setShown(shown === problem.ref ? null : problem.ref)}
                  >
                    {shown === problem.ref ? 'Hide code' : 'View code'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* The problem beside the code, not on another screen. Judging means reading
          the two together, and an organiser flipping between tabs to recall what was
          asked is how inconsistent marking happens. */}
      {shown !== null && <ReviewPane problem={detail.problems.find((p) => p.ref === shown)!} />}
    </section>
  );
}

/**
 * One problem, ready to mark: what was asked on the left, what was written on the
 * right.
 *
 * Shows the statement, the I/O contract, the constraints and the worked examples —
 * everything needed to decide whether the code is correct. Deliberately not the
 * reference solution or the hidden tests: those stay in the bank on disk, and an
 * organiser who wants them can open the file.
 */
function ReviewPane({ problem }: { problem: SubmittedProblem }) {
  return (
    <div className="admin__review">
      <div className="admin__review-brief">
        <p className="admin__label">
          {problem.ref} · {problem.difficulty} · {problem.points} points
        </p>
        <h4 className="admin__review-title">{problem.title}</h4>

        {problem.problemStatement !== null && (
          <p className="admin__review-text">{problem.problemStatement}</p>
        )}

        {problem.inputFormat !== null && (
          <>
            <p className="admin__label">Input</p>
            <p className="admin__review-text">{problem.inputFormat}</p>
          </>
        )}
        {problem.outputFormat !== null && (
          <>
            <p className="admin__label">Output</p>
            <p className="admin__review-text">{problem.outputFormat}</p>
          </>
        )}
        {problem.constraints !== null && (
          <>
            <p className="admin__label">Constraints</p>
            <p className="admin__review-text">{problem.constraints}</p>
          </>
        )}

        {problem.visibleTests.length > 0 && (
          <>
            <p className="admin__label">Examples</p>
            {problem.visibleTests.map((test, i) => (
              // Positional and never reordered.
              // eslint-disable-next-line react/no-array-index-key
              <pre className="admin__review-example" key={i}>
                in  {test.input}
                {'\n'}out {test.expectedOutput}
              </pre>
            ))}
          </>
        )}
      </div>

      <div>
        <p className="admin__label">
          Submitted code
          {problem.awardedPoints !== null && ` · awarded ${problem.awardedPoints}`}
        </p>
        <pre className="admin__code">
          {problem.sourceCode ?? '(the participant never typed in this problem)'}
        </pre>
      </div>
    </div>
  );
}

function toneOf(status: SubmissionRow['evaluationStatus']): string {
  if (status === 'EVALUATED') return 'open';
  if (status === 'PARTIAL') return 'full';
  return 'closed';
}
