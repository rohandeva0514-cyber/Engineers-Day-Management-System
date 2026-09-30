package in.mittechkernel.registration.arena.entity;

import in.mittechkernel.registration.arena.execution.ExecutionStatus;
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
 * One execution, for audit.
 *
 * <p>Records the shape of what happened and none of the content: no source, no test
 * inputs, no expected outputs, no participant stdout. An organiser settling a
 * dispute needs to know that a submission was made, when, and how it was judged -
 * not to re-read everyone's code, which is already on {@code attempt_problem}.
 *
 * <p>Admin-only. No participant response is built from this type.
 */
@Entity
@Table(name = "arena_run")
public class ArenaRun {

    /** Which test set was executed. */
    public enum Kind {
        /** Visible tests, at the participant's request. Changes no status. */
        RUN,
        /** Hidden tests. The only path that can mark a problem solved. */
        SUBMIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_problem_id", nullable = false, updatable = false)
    private AttemptProblem attemptProblem;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 8, nullable = false, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 24, nullable = false, updatable = false)
    private ExecutionStatus verdict;

    @Column(name = "tests_passed", nullable = false, updatable = false)
    private int testsPassed;

    @Column(name = "tests_total", nullable = false, updatable = false)
    private int testsTotal;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ArenaRun() {
        // for JPA
    }

    public ArenaRun(AttemptProblem attemptProblem, Kind kind, ExecutionStatus verdict,
                    int testsPassed, int testsTotal, long durationMs) {
        this.attemptProblem = attemptProblem;
        this.kind = kind;
        this.verdict = verdict;
        this.testsPassed = testsPassed;
        this.testsTotal = testsTotal;
        this.durationMs = (int) Math.min(durationMs, Integer.MAX_VALUE);
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public ExecutionStatus getVerdict() {
        return verdict;
    }

    public int getTestsPassed() {
        return testsPassed;
    }

    public int getTestsTotal() {
        return testsTotal;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
