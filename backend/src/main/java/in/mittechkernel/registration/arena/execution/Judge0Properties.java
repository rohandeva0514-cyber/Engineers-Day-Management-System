package in.mittechkernel.registration.arena.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Judge0 connection and sandbox limits.
 *
 * <p>Every value here is server-side. None of it is accepted from a request, and
 * there is no endpoint through which a participant could propose a longer timeout,
 * more memory, or a different language id - the limits are attached to each
 * submission by the engine, from this object, every time.
 *
 * @param baseUrl        where the self-hosted judge lives. Reachable from the
 *                       backend only; the browser never calls it.
 * @param authToken      optional {@code X-Auth-Token}, when the deployment sets one
 * @param cpuTimeLimitSeconds per-submission CPU ceiling
 * @param wallTimeLimitSeconds real-time ceiling, which also catches a program
 *                       blocked on input rather than burning CPU
 * @param memoryLimitKb  address-space ceiling
 * @param maxOutputChars how much stdout/stderr is kept before truncation
 * @param connectTimeoutMs transport-level connect timeout
 * @param readTimeoutMs  transport-level read timeout for ONE call. With the async
 *                       flow every call is short - a create or a status read - so
 *                       this is a backstop against a hung socket, not the mechanism
 *                       that waits for a verdict. That is {@code pollBudgetMs}.
 * @param pollIntervalMs first gap between status polls. Ramps up to
 *                       {@code maxPollIntervalMs} so a slow compile does not
 *                       generate hundreds of requests.
 * @param maxPollIntervalMs ceiling on that ramp
 * @param pollBudgetMs   how long to keep polling ONE submission before giving up.
 *                       This is what stops a wedged judge holding a Tomcat thread
 *                       open indefinitely, and it must outlast the sandbox's own
 *                       wall limit plus compile time or slow-but-valid submissions
 *                       would be abandoned.
 */
@ConfigurationProperties(prefix = "app.judge0")
public record Judge0Properties(
        String baseUrl,
        String authToken,
        double cpuTimeLimitSeconds,
        double wallTimeLimitSeconds,
        int memoryLimitKb,
        int maxOutputChars,
        int connectTimeoutMs,
        int readTimeoutMs,
        int pollIntervalMs,
        int maxPollIntervalMs,
        int pollBudgetMs) {

    public Judge0Properties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "http://localhost:2358"
                : baseUrl.replaceAll("/+$", "");

        // Defaults matching the locked architecture: 2s CPU, 256 MB, 64 KB output.
        // A zero here means the property was not set, not that someone wants a
        // zero-second limit.
        if (cpuTimeLimitSeconds <= 0) cpuTimeLimitSeconds = 2.0;
        if (wallTimeLimitSeconds <= 0) wallTimeLimitSeconds = 10.0;
        if (memoryLimitKb <= 0) memoryLimitKb = 256 * 1024;
        if (maxOutputChars <= 0) maxOutputChars = 64 * 1024;
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3_000;

        // Short on purpose. Under the async flow no single call waits for a verdict,
        // so a call taking more than a few seconds means the socket is wedged.
        if (readTimeoutMs <= 0) readTimeoutMs = 10_000;

        if (pollIntervalMs <= 0) pollIntervalMs = 150;
        if (maxPollIntervalMs <= 0) maxPollIntervalMs = 750;
        if (maxPollIntervalMs < pollIntervalMs) maxPollIntervalMs = pollIntervalMs;

        // Comfortably past the 10s wall limit, with room for a cold Java or C++
        // compile on top. Anything still unresolved after this is the judge failing,
        // not the participant's program running long.
        if (pollBudgetMs <= 0) pollBudgetMs = 25_000;
    }
}
