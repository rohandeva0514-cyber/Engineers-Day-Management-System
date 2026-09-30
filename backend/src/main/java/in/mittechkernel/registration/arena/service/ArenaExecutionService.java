package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.dto.ProblemDtos.RunResultResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.SubmitResultResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.TestOutcome;
import in.mittechkernel.registration.arena.entity.ArenaRun;
import in.mittechkernel.registration.arena.entity.AttemptProblem;
import in.mittechkernel.registration.arena.execution.ExecutionEngine;
import in.mittechkernel.registration.arena.execution.ExecutionReport;
import in.mittechkernel.registration.arena.execution.ExecutionStatus;
import in.mittechkernel.registration.arena.execution.ExecutionUnavailableException;
import in.mittechkernel.registration.arena.repository.ArenaRunRepository;
import in.mittechkernel.registration.arena.repository.AttemptProblemRepository;
import in.mittechkernel.registration.arena.service.ArenaWorkspaceService.ResolvedProblem;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Run and Submit.
 *
 * <p>The two are the same machinery pointed at different test sets, and the
 * difference between them is the entire security model of this phase:
 *
 * <pre>
 *   RUN     visible tests   full per-case detail returned   changes no status
 *   SUBMIT  hidden tests    a boolean and a verdict only    final, may mark SOLVED
 * </pre>
 *
 * <p>Authorisation is not re-implemented here. {@code ArenaWorkspaceService.resolve}
 * applies every Phase C guard - arena ACTIVE, attempt running and un-expired, slot
 * owned by this attempt, problem in the attempt's locked language - and this class
 * adds only what execution itself requires.
 */
@Service
public class ArenaExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ArenaExecutionService.class);

    /**
     * Ceiling on submitted source.
     *
     * <p>Matches the draft ceiling. Execution accepts code that was never saved -
     * a participant may hit Run before autosave fires - so the bound has to exist on
     * this path too rather than being inherited from the draft path.
     */
    private static final int MAX_SOURCE_BYTES = 64 * 1024;

    private final ArenaWorkspaceService workspace;
    private final ExecutionEngine engine;
    private final AttemptProblemRepository slotRepository;
    private final ArenaRunRepository runRepository;
    private final boolean executionEnabled;

    public ArenaExecutionService(ArenaWorkspaceService workspace,
                                 ExecutionEngine engine,
                                 AttemptProblemRepository slotRepository,
                                 ArenaRunRepository runRepository,
                                 @Value("${app.arena.execution-enabled:true}") boolean executionEnabled) {
        this.workspace = workspace;
        this.engine = engine;
        this.slotRepository = slotRepository;
        this.runRepository = runRepository;
        this.executionEnabled = executionEnabled;
    }

    /**
     * Refuse when the event is running in manual-evaluation mode.
     *
     * <p>Checked first, before authorisation and before any database read, so a
     * disabled endpoint does no work at all.
     *
     * <p>This is a switch rather than deleted code. The engine, the language mapping
     * and the comparator are all untouched and still tested, so restoring live
     * execution is one property - and meanwhile the refusal is enforced by the server
     * rather than by the UI not offering a button.
     */
    private void requireExecutionEnabled() {
        if (!executionEnabled) {
            throw new ApiException(ApiErrorCode.EXECUTION_DISABLED,
                    "Running code is not part of this event. Your saved work is "
                            + "submitted for review by the organisers.",
                    Map.of());
        }
    }

    // --------------------------------------------------------------------- run

    /**
     * Execute against the visible tests.
     *
     * <p>Proves nothing and decides nothing. It does not mark a problem solved, does
     * not consume anything, and carries no penalty for failing - a participant may
     * run as often as they like. The run counter exists for the admin roster, not
     * for scoring.
     *
     * <p>The code is taken from the request rather than the stored draft so that Run
     * reflects what is on screen right now, including edits the autosave debounce
     * has not yet flushed.
     */
    @Transactional
    public RunResultResponse run(Long attemptId, String ref, String sourceCode) {
        requireExecutionEnabled();

        ResolvedProblem resolved = workspace.resolve(attemptId, ref);
        AttemptProblem slot = resolved.slot();

        if (slot.isSubmitted()) {
            throw ArenaWorkspaceService.problemAlreadySubmitted();
        }
        requireSource(sourceCode);

        ExecutionReport report = execute(
                resolved, sourceCode, resolved.problem().visibleTests(), ArenaRun.Kind.RUN);

        Instant now = Instant.now();
        slot.recordRun(now);
        slotRepository.save(slot);

        List<TestOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < report.cases().size(); i++) {
            ExecutionReport.CaseResult result = report.cases().get(i);
            // Safe to echo: these came from visible_tests, which the participant is
            // already looking at on the problem panel.
            outcomes.add(new TestOutcome(
                    i + 1,
                    result.passed(),
                    result.status().name(),
                    result.input(),
                    result.expectedOutput(),
                    result.actualOutput(),
                    result.timeMs()));
        }

        return new RunResultResponse(
                now,
                slot.getRef(),
                report.status().name(),
                report.passedCount(),
                report.totalCount(),
                outcomes,
                report.compileOutput(),
                firstStderr(report),
                slot.getRunCount(),
                slot.getStatus().name());
    }

    // ------------------------------------------------------------------ submit

    /**
     * Execute against the hidden tests and finalise the problem.
     *
     * <p>This is the only path in the system that can set SOLVED, and it does so
     * only when every hidden test passes. Compilation success does not qualify;
     * passing the visible tests does not qualify; the client asserting anything does
     * not qualify.
     *
     * <p>Irreversible per problem. The lock is written whatever the verdict, so a
     * failed submission cannot be retried until it happens to pass - and the eleven
     * other problems are unaffected, because this is not the end of the mission.
     *
     * <p><b>The response deliberately describes none of the hidden suite.</b> Not
     * how many tests there are, not how many failed, not which. See
     * {@code SubmitResultResponse}.
     */
    @Transactional
    public SubmitResultResponse submit(Long attemptId, String ref, String sourceCode) {
        requireExecutionEnabled();

        ResolvedProblem resolved = workspace.resolve(attemptId, ref);
        AttemptProblem slot = resolved.slot();

        if (slot.isSubmitted()) {
            throw ArenaWorkspaceService.problemAlreadySubmitted();
        }
        requireSource(sourceCode);

        ExecutionReport report = execute(
                resolved, sourceCode, resolved.problem().hiddenTests(), ArenaRun.Kind.SUBMIT);

        Instant now = Instant.now();
        boolean solved = report.status() == ExecutionStatus.ACCEPTED && report.allPassed();

        // The judged code becomes the code on record, so what was scored is what is
        // stored. saveDraft also moves NOT_ATTEMPTED to ATTEMPTED.
        slot.saveDraft(sourceCode, now);
        slot.recordRun(now);
        slot.markSubmitted(solved, now);
        slotRepository.save(slot);

        log.info("Attempt {} submitted {} -> {}", attemptId, slot.getRef(),
                solved ? "SOLVED" : report.status());

        return new SubmitResultResponse(
                now,
                slot.getRef(),
                report.status().name(),
                solved,
                slot.getSubmittedAt(),
                // Compiler diagnostics are about the participant's own source, so
                // they are safe and genuinely useful. Nothing else from the hidden
                // run crosses.
                report.compileOutput(),
                slot.getStatus().name());
    }

    // ----------------------------------------------------------------- internals

    /**
     * Run the engine and record the attempt, translating an outage into a refusal.
     *
     * <p>The distinction this method exists to protect: an {@link
     * ExecutionUnavailableException} is NOT a verdict. Nothing is persisted, no
     * status moves, the draft is untouched, and the participant is told to retry.
     * Recording an outage as a wrong answer - or worse, as a used-up submission -
     * would score an infrastructure failure against a student.
     */
    private ExecutionReport execute(ResolvedProblem resolved, String sourceCode,
                                    List<in.mittechkernel.registration.arena.bank.TestCase> tests,
                                    ArenaRun.Kind kind) {
        ExecutionReport report;
        try {
            report = engine.execute(resolved.language(), sourceCode, tests);
        } catch (ExecutionUnavailableException outage) {
            // Logged with its cause; the participant gets a fixed, safe string that
            // names no host, port, container or stack frame.
            log.error("Execution engine unavailable for attempt {} problem {}",
                    resolved.attempt().getId(), resolved.slot().getRef(), outage);
            throw new ApiException(ApiErrorCode.EXECUTION_UNAVAILABLE,
                    "The execution service is temporarily unavailable. "
                            + "Your code has not been lost - please try again in a moment.",
                    Map.of());
        }

        if (report.status() == ExecutionStatus.INTERNAL_ERROR) {
            // The judge answered, but with something that is not a verdict about
            // this code. Treated exactly like an outage rather than as a failure.
            log.error("Execution engine reported an internal error for attempt {} problem {}",
                    resolved.attempt().getId(), resolved.slot().getRef());
            throw new ApiException(ApiErrorCode.EXECUTION_UNAVAILABLE,
                    "The execution service could not run your code. "
                            + "Your code has not been lost - please try again in a moment.",
                    Map.of());
        }

        runRepository.save(new ArenaRun(
                resolved.slot(), kind, report.status(),
                report.passedCount(), report.totalCount(), report.durationMs()));

        return report;
    }

    private static void requireSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "There is no code to run.", Map.of());
        }
        if (sourceCode.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            throw new ApiException(ApiErrorCode.DRAFT_TOO_LARGE,
                    "That submission is too large.",
                    Map.of("maxBytes", MAX_SOURCE_BYTES));
        }
    }

    /** The first stderr any visible case produced - useful when a program crashed. */
    private static String firstStderr(ExecutionReport report) {
        return report.cases().stream()
                .map(ExecutionReport.CaseResult::stderr)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
