package in.mittechkernel.registration.arena.entity;

import in.mittechkernel.registration.arena.bank.DebugProblem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * What one participant did with one problem.
 *
 * <p>Holds the participant's side of it only - status, draft, revision. The problem
 * itself is never copied here: {@code problemId} is the bank's own string id and
 * {@code ref} is the board handle, and both are resolved against the JSON bank when
 * needed. That is what keeps corrected solutions and hidden tests out of PostgreSQL
 * altogether.
 */
@Entity
@Table(name = "attempt_problem")
public class AttemptProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private ArenaAttempt attempt;

    @Column(name = "problem_id", length = 32, nullable = false, updatable = false)
    private String problemId;

    @Column(name = "ref", length = 8, nullable = false, updatable = false)
    private String ref;

    @Column(name = "difficulty", length = 8, nullable = false, updatable = false)
    private String difficulty;

    @Column(name = "ordinal", nullable = false, updatable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private AttemptProblemStatus status;

    @Column(name = "draft_code")
    private String draftCode;

    @Column(name = "draft_revision", nullable = false)
    private int draftRevision;

    @Column(name = "run_count", nullable = false)
    private int runCount;

    @Column(name = "solved_at")
    private Instant solvedAt;

    /**
     * Points an organiser awarded by hand, or null if nobody has looked yet.
     *
     * <p>Null and 0 mean different things: "not evaluated" and "evaluated, no
     * credit". An organiser working through a pile of submissions needs to tell those
     * apart, so this must never be defaulted to zero.
     */
    @Column(name = "awarded_points")
    private Integer awardedPoints;

    /**
     * When this problem was submitted. Set once, never cleared.
     *
     * <p>Separate from {@link #solvedAt} on purpose: a submission that fails its
     * hidden tests still locks the problem. Folding the two together would either
     * let a failed submission be retried until it passed, or mark a wrong answer
     * solved.
     */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttemptProblem() {
        // for JPA
    }

    /**
     * Materialise one board slot from a bank problem.
     *
     * <p>Copies the identifiers and the shape of the tile - never the content. Note
     * what is not copied: statement, buggy code, tests, solution. Those are read
     * from the bank on demand, so this row stays a record of behaviour rather than a
     * second, divergent copy of the problem.
     */
    public AttemptProblem(ArenaAttempt attempt, DebugProblem problem, Instant now) {
        this.attempt = attempt;
        this.problemId = problem.id();
        this.ref = problem.ref();
        this.difficulty = problem.difficulty().name();
        this.ordinal = problem.ordinal();
        this.status = AttemptProblemStatus.NOT_ATTEMPTED;
        this.draftRevision = 0;
        this.runCount = 0;
        this.updatedAt = now;
    }

    /**
     * Record a draft.
     *
     * <p>Moves NOT_ATTEMPTED to ATTEMPTED and stops there. A save is not a
     * submission and is not evidence of a correct answer - a participant who types
     * one character has attempted the problem, not solved it. Only the judge can set
     * SOLVED, and not until Phase D.
     *
     * <p>A problem already marked SOLVED stays SOLVED here; re-evaluating it belongs
     * to the judge, which is the only thing that can know whether the edit broke the
     * fix.
     */
    public void saveDraft(String code, Instant now) {
        this.draftCode = code;
        this.draftRevision++;
        this.updatedAt = now;

        if (this.status == AttemptProblemStatus.NOT_ATTEMPTED) {
            this.status = AttemptProblemStatus.ATTEMPTED;
        }
    }

    /** Count an execution. Not a score - there is no penalty for running code. */
    public void recordRun(Instant now) {
        this.runCount++;
        this.updatedAt = now;
    }

    /**
     * Finalise this problem.
     *
     * <p>Called only from the hidden-test path, and only once -
     * {@link #isSubmitted()} guards it upstream. {@code solved} comes from the judge
     * having passed every hidden test; nothing else in the system can set it, which
     * is what keeps the badge on the board honest.
     */
    public void markSubmitted(boolean solved, Instant now) {
        this.submittedAt = now;
        this.updatedAt = now;

        if (solved) {
            this.status = AttemptProblemStatus.SOLVED;
            this.solvedAt = now;
        } else if (this.status == AttemptProblemStatus.NOT_ATTEMPTED) {
            // Submitted without ever saving a draft: still an attempt.
            this.status = AttemptProblemStatus.ATTEMPTED;
        }
    }

    /** True once submitted. The problem is then read-only for the rest of the mission. */
    public boolean isSubmitted() {
        return submittedAt != null;
    }

    /**
     * Record an organiser's judgement.
     *
     * <p>Sets nothing else. In particular it does NOT touch {@code status} or
     * {@code solvedAt}: those describe what the judge determined, and in manual mode
     * no judge ran. Conflating an organiser's award with a verified SOLVED would make
     * the two indistinguishable afterwards, which is exactly what someone disputing a
     * result needs to be able to separate.
     */
    public void award(Integer points, Instant now) {
        this.awardedPoints = points;
        this.updatedAt = now;
    }

    /** Null until an organiser has evaluated this problem. */
    public Integer getAwardedPoints() {
        return awardedPoints;
    }

    public boolean isEvaluated() {
        return awardedPoints != null;
    }

    public boolean hasDraft() {
        return draftCode != null && !draftCode.isEmpty();
    }

    public Long getId() {
        return id;
    }

    public ArenaAttempt getAttempt() {
        return attempt;
    }

    public String getProblemId() {
        return problemId;
    }

    public String getRef() {
        return ref;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public AttemptProblemStatus getStatus() {
        return status;
    }

    public String getDraftCode() {
        return draftCode;
    }

    public int getDraftRevision() {
        return draftRevision;
    }

    public int getRunCount() {
        return runCount;
    }

    public Instant getSolvedAt() {
        return solvedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
