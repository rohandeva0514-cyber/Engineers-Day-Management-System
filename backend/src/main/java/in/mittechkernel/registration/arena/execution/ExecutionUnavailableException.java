package in.mittechkernel.registration.arena.execution;

/**
 * The execution engine failed - not the participant's code.
 *
 * <p>Thrown when the judge is unreachable, times out at the transport level,
 * answers with something unparseable, or is misconfigured. It exists as a distinct
 * type precisely so that no caller can accidentally treat it as a verdict: a
 * participant whose submission hit an outage must not be marked wrong, must not be
 * marked solved, must keep their draft, and must be able to retry.
 *
 * <p>The message is for logs. What the participant sees is a fixed, safe string
 * chosen by the controller layer - the cause may contain a hostname, a port, or a
 * stack trace, and none of that belongs in a response.
 */
public class ExecutionUnavailableException extends RuntimeException {

    public ExecutionUnavailableException(String message) {
        super(message);
    }

    public ExecutionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
