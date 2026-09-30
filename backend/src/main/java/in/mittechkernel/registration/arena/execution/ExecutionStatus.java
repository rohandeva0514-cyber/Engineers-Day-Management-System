package in.mittechkernel.registration.arena.execution;

/**
 * The verdict a participant is shown, normalised away from any engine.
 *
 * <p>Judge0 has more than a dozen status ids, several of which differ only in which
 * signal killed a process. A participant does not need that distinction and an
 * organiser reading a dispute does not either - what matters is "it did not
 * compile", "it crashed", "it ran too long", "it produced the wrong answer".
 *
 * <p>Keeping this enum engine-independent is also what stops Judge0's vocabulary
 * leaking into the API. Nothing downstream - the DTOs, the database, the frontend -
 * knows a status id exists.
 */
public enum ExecutionStatus {

    /** Every test in the executed set produced the expected output. */
    ACCEPTED,

    /** It ran, but at least one test produced something other than expected. */
    WRONG_ANSWER,

    /** The source did not compile. */
    COMPILATION_ERROR,

    /** It compiled but died - a signal, a non-zero exit, an uncaught exception. */
    RUNTIME_ERROR,

    /** It exceeded the CPU or wall-clock limit. */
    TIME_LIMIT_EXCEEDED,

    /**
     * Something went wrong that is not the participant's fault.
     *
     * <p>Never recorded as a wrong answer, never marks a problem solved or failed.
     * The participant keeps their draft and may retry. This is the distinction
     * between "your code is wrong" and "our judge is broken", and collapsing the
     * two would mean an infrastructure outage silently scored as failures.
     */
    INTERNAL_ERROR
}
