package in.mittechkernel.registration.arena.execution;

import java.util.List;

/**
 * What an engine observed, before anything decides what a participant may see.
 *
 * <p>This is the internal result. It carries per-case detail for <em>every</em> case
 * it was given, including hidden ones - which is exactly why it is not a DTO and
 * never reaches a controller. The mappers in {@code ArenaExecutionService} build the
 * participant-facing shapes from it, and the hidden path builds one that carries no
 * per-case detail at all.
 *
 * @param status       the overall verdict
 * @param cases        one entry per executed case, in the order given
 * @param compileOutput compiler diagnostics, or null. Truncated by the engine.
 * @param durationMs   wall time for the whole batch, measured by the backend
 */
public record ExecutionReport(
        ExecutionStatus status,
        List<CaseResult> cases,
        String compileOutput,
        long durationMs) {

    public ExecutionReport {
        cases = List.copyOf(cases);
    }

    /**
     * One executed case.
     *
     * @param input          the case's input. Safe for a visible case, NOT for a
     *                       hidden one - which is why the hidden mapper drops it.
     * @param expectedOutput likewise
     * @param actualOutput   what the participant's program printed, truncated
     * @param stderr         what it printed to stderr, truncated
     */
    public record CaseResult(
            boolean passed,
            ExecutionStatus status,
            String input,
            String expectedOutput,
            String actualOutput,
            String stderr,
            Integer timeMs) {
    }

    public int passedCount() {
        return (int) cases.stream().filter(CaseResult::passed).count();
    }

    public int totalCount() {
        return cases.size();
    }

    /** True only when every case ran and passed. */
    public boolean allPassed() {
        return !cases.isEmpty() && cases.stream().allMatch(CaseResult::passed);
    }
}
