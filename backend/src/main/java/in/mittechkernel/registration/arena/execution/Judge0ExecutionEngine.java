package in.mittechkernel.registration.arena.execution;

import com.fasterxml.jackson.databind.JsonNode;
import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes participant code on the self-hosted Judge0.
 *
 * <h2>Judge0 sits behind the backend</h2>
 *
 * <p>The browser never learns that this class exists. It does not receive the
 * judge's URL, its submission tokens, its status ids, or its timings. Everything it
 * gets is the normalised {@link ExecutionReport} this class produces - which is also
 * what makes swapping the engine a one-package change.
 *
 * <h2>Base64 in both directions</h2>
 *
 * <p>Source, stdin and expected output go over as base64 and results come back the
 * same way. Participant code is arbitrary text: it contains quotes, backslashes,
 * NUL-adjacent bytes and whatever a student pasted out of a PDF. Base64 removes an
 * entire class of "works until someone types a backtick" bugs, at the cost of a few
 * lines here.
 *
 * <h2>Create, then poll - never {@code wait=true}</h2>
 *
 * <p>Judge0's synchronous mode holds the HTTP connection open until the verdict is
 * ready, which reads as the obvious choice for workloads measured in milliseconds.
 * It does not survive the one case that matters most here.
 *
 * <p>On this deployment a submission that exceeds its limits - an infinite loop,
 * which is close to the most likely thing a student writes in a debugging contest -
 * never causes {@code wait=true} to return. The request was eventually killed by the
 * client's own read timeout and surfaced as {@code HttpTimeoutException}, so a
 * perfectly ordinary participant mistake was reported as the judge being down.
 *
 * <p>The same submission resolves correctly through the asynchronous API: the create
 * call returns a token immediately, and polling reports status 5, Time Limit
 * Exceeded, in about two seconds. Isolate and Judge0 were both doing their job; only
 * the transport was wrong. So this engine creates with {@code wait=false} and polls
 * {@code /submissions/{token}} to a terminal status.
 *
 * <p>Raising the read timeout would not have fixed it. There is no timeout large
 * enough for a call that never returns, and a larger one only holds the Tomcat
 * thread longer before failing the same way.
 *
 * <h2>One submission per test case</h2>
 *
 * <p>Judge0 has a batch endpoint, but the problems here run two to seven cases of a
 * few milliseconds each. Sequential submissions are simpler, and simpler is worth
 * more than a fractional saving on an operation a participant performs a few dozen
 * times.
 *
 * <p>Execution stops early on a compilation error: the source failed to build, and
 * running the remaining cases would produce six identical failures and six pointless
 * round trips.
 *
 * <h2>Statelessness</h2>
 *
 * <p>No field on this class changes after construction. {@link RestTemplate} is
 * thread-safe once built, every request carries its own body, and nothing is shared
 * between concurrent executions - thirty participants pressing Run at once produce
 * thirty independent conversations.
 */
@Component
public class Judge0ExecutionEngine implements ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(Judge0ExecutionEngine.class);

    /**
     * Judge0 status ids, normalised.
     *
     * <p>The full list is longer; these are the ones that can result from running a
     * single submission to completion. Anything unrecognised is treated as an
     * infrastructure failure rather than guessed at - see {@link #statusOf}.
     */
    /** The two non-terminal statuses. These, and only these, continue polling. */
    private static final int STATUS_IN_QUEUE = 1;
    private static final int STATUS_PROCESSING = 2;

    private static final int STATUS_ACCEPTED = 3;
    private static final int STATUS_WRONG_ANSWER = 4;
    private static final int STATUS_TIME_LIMIT = 5;
    private static final int STATUS_COMPILATION_ERROR = 6;
    private static final int RUNTIME_ERROR_FIRST = 7;
    private static final int RUNTIME_ERROR_LAST = 12;
    private static final int STATUS_INTERNAL_ERROR = 13;
    private static final int STATUS_EXEC_FORMAT_ERROR = 14;

    private final RestTemplate http;
    private final Judge0Properties properties;

    /**
     * {@code @Autowired} is load-bearing: the package-private test seam below makes
     * this a two-constructor class, and Spring will not pick one by itself - it falls
     * back to looking for a no-arg constructor and fails the whole context. This
     * annotation says which one is the production entry point.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public Judge0ExecutionEngine(RestTemplateBuilder builder, Judge0Properties properties) {
        this(builder
                        .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                        .readTimeout(Duration.ofMillis(properties.readTimeoutMs()))
                        .build(),
                properties);
    }

    /**
     * Test seam.
     *
     * <p>Lets a test bind {@code MockRestServiceServer} to the template and script
     * the create-then-poll conversation, which is the only way to assert the polling
     * loop - queue, processing, terminal - without a live judge. Package-private: it
     * is not a second way to configure the engine in production.
     */
    Judge0ExecutionEngine(RestTemplate http, Judge0Properties properties) {
        this.http = http;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "judge0";
    }

    @Override
    public ExecutionReport execute(ArenaLanguage language, String sourceCode, List<TestCase> tests) {
        if (sourceCode == null || sourceCode.isBlank()) {
            // Nothing to run. Not an engine failure and not worth a round trip.
            return new ExecutionReport(ExecutionStatus.COMPILATION_ERROR, List.of(),
                    "There is no code to run.", 0L);
        }

        int languageId = Judge0Languages.idFor(language);
        long startedAt = System.nanoTime();

        List<ExecutionReport.CaseResult> results = new ArrayList<>();
        String compileOutput = null;
        ExecutionStatus overall = ExecutionStatus.ACCEPTED;

        for (TestCase test : tests) {
            JsonNode response = submit(languageId, sourceCode, test);

            int statusId = response.path("status").path("id").asInt(-1);
            ExecutionStatus status = statusOf(statusId);

            String stdout = decode(response.path("stdout").asText(null));
            String stderr = decode(response.path("stderr").asText(null));
            String compile = decode(response.path("compile_output").asText(null));

            if (status == ExecutionStatus.COMPILATION_ERROR) {
                // The build failed; every remaining case would fail identically.
                return new ExecutionReport(
                        ExecutionStatus.COMPILATION_ERROR,
                        List.of(),
                        truncate(compile),
                        elapsedMs(startedAt));
            }

            // Judge0 can decide a case by itself when given expected_output, but the
            // comparison rules are ours and are documented, so the verdict is taken
            // from OutputComparator rather than from the judge's own status.
            boolean passed = status != ExecutionStatus.TIME_LIMIT_EXCEEDED
                    && status != ExecutionStatus.RUNTIME_ERROR
                    && status != ExecutionStatus.INTERNAL_ERROR
                    && OutputComparator.matches(test.expectedOutput(), stdout);

            if (!passed && overall == ExecutionStatus.ACCEPTED) {
                overall = switch (status) {
                    case TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, INTERNAL_ERROR -> status;
                    default -> ExecutionStatus.WRONG_ANSWER;
                };
            }

            results.add(new ExecutionReport.CaseResult(
                    passed,
                    passed ? ExecutionStatus.ACCEPTED : status,
                    test.input(),
                    test.expectedOutput(),
                    truncate(stdout),
                    truncate(stderr),
                    millisOf(response)));

            if (compile != null && compileOutput == null) {
                compileOutput = truncate(compile);
            }
        }

        return new ExecutionReport(overall, results, compileOutput, elapsedMs(startedAt));
    }

    // ------------------------------------------------------------------ transport

    /** One test case: create the submission, then wait for its verdict. */
    private JsonNode submit(int languageId, String sourceCode, TestCase test) {
        return awaitVerdict(createSubmission(languageId, sourceCode, test));
    }

    /**
     * Create the submission and take the token. Does not wait for a verdict.
     *
     * <p>Every limit is attached here, from configuration. There is no code path by
     * which a caller - let alone a request - can influence them.
     */
    private String createSubmission(int languageId, String sourceCode, TestCase test) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", languageId);
        body.put("source_code", encode(sourceCode));
        body.put("stdin", encode(test.input()));

        // Sent so the judge's own status is meaningful in its logs. Our verdict
        // still comes from OutputComparator - see execute().
        body.put("expected_output", encode(test.expectedOutput()));

        body.put("cpu_time_limit", properties.cpuTimeLimitSeconds());
        body.put("wall_time_limit", properties.wallTimeLimitSeconds());
        body.put("memory_limit", properties.memoryLimitKb());
        body.put("redirect_stderr_to_stdout", false);

        // Sent explicitly rather than relying on the default. This deployment's
        // /config_info reports allow_enable_network=true, meaning a submission that
        // asked for network access would get it. Nothing in this codebase asks - and
        // stating that on every submission means it stays true even if the judge's
        // defaults change under us. Participant code has no business making
        // outbound connections.
        body.put("enable_network", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.authToken() != null && !properties.authToken().isBlank()) {
            headers.set("X-Auth-Token", properties.authToken());
        }

        // wait=false. See the class note: holding the connection open for the verdict
        // is what failed, and it failed at the HTTP layer rather than in the sandbox.
        String url = properties.baseUrl() + "/submissions?base64_encoded=true&wait=false";

        try {
            ResponseEntity<JsonNode> response = http.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);

            JsonNode payload = response.getBody();
            String token = payload == null ? null : payload.path("token").asText(null);

            if (token == null || token.isBlank()) {
                throw new ExecutionUnavailableException(
                        "The judge accepted a submission but returned no token.");
            }
            return token;

        } catch (ExecutionUnavailableException rethrow) {
            throw rethrow;
        } catch (RestClientException transportFailure) {
            // The message can carry the judge's host and port. Logged, never
            // returned - the controller substitutes a fixed participant-safe string.
            log.error("Judge0 submission could not be created", transportFailure);
            throw new ExecutionUnavailableException(
                    "The execution service did not accept the submission.", transportFailure);
        }
    }

    /**
     * Poll one submission until the judge reaches a terminal status.
     *
     * <p>Statuses 1 (In Queue) and 2 (Processing) mean "not yet" and are the only
     * two that continue the loop. Everything else is a verdict and returns
     * immediately - including 13 and 14, which {@link #statusOf} turns into
     * INTERNAL_ERROR for the caller to treat as an outage.
     *
     * <h2>Bounded, deliberately</h2>
     *
     * <p>The loop is capped by a wall-clock budget, not by an attempt count, because
     * what needs bounding is how long a Tomcat thread can be held - and that is time,
     * not iterations. A judge that never resolves a submission therefore costs one
     * request {@code pollBudgetMs} and then releases it, rather than occupying a
     * thread until the container is restarted.
     *
     * <p>The interval ramps from {@code pollIntervalMs} to {@code maxPollIntervalMs}.
     * A short first gap keeps the common case - a program that finishes in
     * milliseconds - feeling immediate, while the ramp stops a slow C++ or Java
     * compile from generating a hundred status requests.
     */
    private JsonNode awaitVerdict(String token) {
        String url = properties.baseUrl() + "/submissions/" + token
                + "?base64_encoded=true&fields=*";

        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(properties.pollBudgetMs());
        long interval = properties.pollIntervalMs();

        while (true) {
            JsonNode payload = pollOnce(url, token);
            int statusId = payload.path("status").path("id").asInt(-1);

            if (statusId != STATUS_IN_QUEUE && statusId != STATUS_PROCESSING) {
                return payload;
            }

            if (System.nanoTime() >= deadline) {
                // Not a verdict. The submission may well still be running inside the
                // judge; we simply stop waiting for it, and the caller reports an
                // outage rather than marking anyone's code wrong.
                log.error("Judge0 submission {} did not resolve within {} ms (last status {})",
                        token, properties.pollBudgetMs(), statusId);
                throw new ExecutionUnavailableException(
                        "The execution service did not return a result in time.");
            }

            sleep(interval);
            interval = Math.min(interval * 2, properties.maxPollIntervalMs());
        }
    }

    private JsonNode pollOnce(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (properties.authToken() != null && !properties.authToken().isBlank()) {
            headers.set("X-Auth-Token", properties.authToken());
        }

        try {
            ResponseEntity<JsonNode> response = http.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

            JsonNode payload = response.getBody();
            if (payload == null || payload.path("status").path("id").isMissingNode()) {
                throw new ExecutionUnavailableException(
                        "The judge returned a status response with no status.");
            }
            return payload;

        } catch (ExecutionUnavailableException rethrow) {
            throw rethrow;
        } catch (RestClientException transportFailure) {
            log.error("Judge0 status read failed for submission {}", token, transportFailure);
            throw new ExecutionUnavailableException(
                    "The execution service did not respond.", transportFailure);
        }
    }

    /**
     * Wait between polls, preserving interrupt semantics.
     *
     * <p>The interrupt flag is restored before throwing. Swallowing it would leave
     * the thread looking un-interrupted to everything above this frame - so a
     * shutdown, or a request the container is trying to cancel, would be ignored by
     * the next blocking call on the same thread.
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExecutionUnavailableException(
                    "Waiting for the execution service was interrupted.", interrupted);
        }
    }

    /**
     * Judge0 status id to our vocabulary.
     *
     * <p>1 and 2 never reach here - {@link #awaitVerdict} keeps polling while the
     * judge reports them, and only hands over a terminal status. Anything
     * unrecognised is treated as INTERNAL_ERROR: inventing a meaning for an unknown
     * status is how a broken judge starts producing wrong answers.
     */
    private static ExecutionStatus statusOf(int statusId) {
        if (statusId == STATUS_ACCEPTED) return ExecutionStatus.ACCEPTED;
        if (statusId == STATUS_WRONG_ANSWER) return ExecutionStatus.WRONG_ANSWER;
        if (statusId == STATUS_TIME_LIMIT) return ExecutionStatus.TIME_LIMIT_EXCEEDED;
        if (statusId == STATUS_COMPILATION_ERROR) return ExecutionStatus.COMPILATION_ERROR;
        if (statusId >= RUNTIME_ERROR_FIRST && statusId <= RUNTIME_ERROR_LAST) {
            return ExecutionStatus.RUNTIME_ERROR;
        }
        if (statusId == STATUS_INTERNAL_ERROR || statusId == STATUS_EXEC_FORMAT_ERROR) {
            return ExecutionStatus.INTERNAL_ERROR;
        }
        return ExecutionStatus.INTERNAL_ERROR;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new String(Base64.getMimeDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            // Judge0 returns plain text for some error paths even in base64 mode.
            return value;
        }
    }

    /** Keeps a runaway program from filling a response, a log, or a screen. */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int max = properties.maxOutputChars();
        return value.length() <= max ? value : value.substring(0, max) + "\n… output truncated";
    }

    private static Integer millisOf(JsonNode response) {
        String time = response.path("time").asText(null);
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(time) * 1000);
        } catch (NumberFormatException unparseable) {
            return null;
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
