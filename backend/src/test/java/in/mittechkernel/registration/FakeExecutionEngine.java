package in.mittechkernel.registration;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.TestCase;
import in.mittechkernel.registration.arena.execution.ExecutionEngine;
import in.mittechkernel.registration.arena.execution.ExecutionReport;
import in.mittechkernel.registration.arena.execution.ExecutionStatus;
import in.mittechkernel.registration.arena.execution.ExecutionUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scripted stand-in for the judge.
 *
 * <p>Most of what Phase D needs to prove has nothing to do with compilers:
 * authorisation, the submission lock, what reaches a response, what a failed
 * submission does to a status. Those tests should be fast, deterministic, and
 * runnable on a laptop with no Judge0 at all - so they run against this.
 *
 * <p>The real engine is exercised separately by {@code Judge0IntegrationTest}, which
 * skips itself when the judge is not up. That split is deliberate: a suite that
 * cannot run without a container is a suite people stop running.
 *
 * <p>Not thread-safe by design for the scripted fields - each test sets a mode and
 * then drives it - but {@link #calls} is atomic so the concurrency test can count
 * executions from several threads.
 */
public class FakeExecutionEngine implements ExecutionEngine {

    public enum Mode {
        /** Every case passes. */
        PASS_ALL,
        /** Every case runs but prints the wrong thing. */
        WRONG_ANSWER,
        COMPILE_ERROR,
        RUNTIME_ERROR,
        TIMEOUT,
        /** The judge answered, but with an internal error rather than a verdict. */
        INTERNAL_ERROR,
        /** The judge could not be reached at all. */
        UNAVAILABLE,
        /** Passes only when the source contains {@link #requiredMarker}. */
        PASS_IF_SOURCE_CONTAINS
    }

    private volatile Mode mode = Mode.PASS_ALL;
    private volatile String requiredMarker = "FIXED";
    private final AtomicInteger calls = new AtomicInteger();

    /** Records what the engine was actually handed, so tests can assert on it. */
    private volatile ArenaLanguage lastLanguage;
    private volatile int lastTestCount;

    public void reset() {
        mode = Mode.PASS_ALL;
        requiredMarker = "FIXED";
        calls.set(0);
        lastLanguage = null;
        lastTestCount = 0;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void passIfSourceContains(String marker) {
        this.mode = Mode.PASS_IF_SOURCE_CONTAINS;
        this.requiredMarker = marker;
    }

    public int calls() {
        return calls.get();
    }

    public ArenaLanguage lastLanguage() {
        return lastLanguage;
    }

    /** How many cases the service handed over - visible count vs hidden count. */
    public int lastTestCount() {
        return lastTestCount;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public ExecutionReport execute(ArenaLanguage language, String sourceCode, List<TestCase> tests) {
        calls.incrementAndGet();
        lastLanguage = language;
        lastTestCount = tests.size();

        if (mode == Mode.UNAVAILABLE) {
            throw new ExecutionUnavailableException("fake judge is down");
        }
        if (mode == Mode.COMPILE_ERROR) {
            return new ExecutionReport(ExecutionStatus.COMPILATION_ERROR, List.of(),
                    "error: expected ';' before '}' token", 12L);
        }
        if (mode == Mode.INTERNAL_ERROR) {
            return new ExecutionReport(ExecutionStatus.INTERNAL_ERROR, List.of(), null, 5L);
        }

        boolean pass = switch (mode) {
            case PASS_ALL -> true;
            case PASS_IF_SOURCE_CONTAINS -> sourceCode != null && sourceCode.contains(requiredMarker);
            default -> false;
        };

        ExecutionStatus caseStatus = switch (mode) {
            case RUNTIME_ERROR -> ExecutionStatus.RUNTIME_ERROR;
            case TIMEOUT -> ExecutionStatus.TIME_LIMIT_EXCEEDED;
            default -> pass ? ExecutionStatus.ACCEPTED : ExecutionStatus.WRONG_ANSWER;
        };

        List<ExecutionReport.CaseResult> cases = new ArrayList<>();
        for (TestCase test : tests) {
            cases.add(new ExecutionReport.CaseResult(
                    pass,
                    pass ? ExecutionStatus.ACCEPTED : caseStatus,
                    test.input(),
                    test.expectedOutput(),
                    pass ? test.expectedOutput() : "not the expected output",
                    caseStatus == ExecutionStatus.RUNTIME_ERROR ? "Segmentation fault" : null,
                    3));
        }

        ExecutionStatus overall = pass ? ExecutionStatus.ACCEPTED : caseStatus;
        return new ExecutionReport(overall, cases, null, 20L);
    }
}
