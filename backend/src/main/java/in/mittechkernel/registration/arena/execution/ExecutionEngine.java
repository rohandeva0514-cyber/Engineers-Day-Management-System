package in.mittechkernel.registration.arena.execution;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.TestCase;

import java.util.List;

/**
 * Runs participant code against a set of test cases.
 *
 * <p>An interface rather than a class because the arena must not be welded to one
 * execution provider. Everything above this line - authorisation, the deadline, the
 * verdict, what a participant is shown - is engine-independent, and replacing
 * Judge0 should touch one package.
 *
 * <p>It deliberately knows nothing about attempts, participants, sessions or
 * problems. It is handed source, a language and a list of cases, and it reports
 * what happened. Every decision about whether that execution was allowed has
 * already been made by the time anything calls this.
 *
 * <h2>The contract on failure</h2>
 *
 * <p>An implementation MUST distinguish a participant's code failing from the
 * engine itself failing. A wrong answer, a crash or a timeout is a
 * {@link ExecutionReport} with the corresponding {@link ExecutionStatus}. An engine
 * that is unreachable, misconfigured, or answering nonsense throws
 * {@link ExecutionUnavailableException}, which callers must never record as a
 * result. Conflating the two turns an outage into a page of wrong answers.
 */
public interface ExecutionEngine {

    /**
     * Execute {@code sourceCode} against every case, in order.
     *
     * <p>Implementations may stop early once the verdict cannot change - a
     * compilation error fails everything, and there is no reason to run twelve
     * cases to discover that twice.
     *
     * @param tests the cases to run. The caller decides whether these are the
     *              visible set or the hidden set; the engine cannot tell and must
     *              not care.
     * @throws ExecutionUnavailableException when the engine itself failed
     */
    ExecutionReport execute(ArenaLanguage language, String sourceCode, List<TestCase> tests);

    /** A short, human-readable name for logs and the admin dashboard. */
    String name();
}
