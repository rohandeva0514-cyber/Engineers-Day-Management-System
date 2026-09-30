package in.mittechkernel.registration.arena.execution;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The Judge0 transport: create with {@code wait=false}, then poll to a terminal
 * status.
 *
 * <p>Scripted against {@code MockRestServiceServer}, so these assert the flow itself
 * - the create call, the URLs, the queue/processing loop, the budget - without a live
 * judge and without waiting on real compilers. {@code Judge0IntegrationTest} covers
 * the same ground against the real thing; this covers the parts a real judge is too
 * fast and too well-behaved to exercise, like a submission that never resolves.
 *
 * <p>Deliberately verifies that {@code wait=true} is gone: that is the bug these
 * tests exist to prevent from returning.
 */
class Judge0TransportTest {

    private static final String BASE = "http://judge.test:2358";
    private static final String TOKEN = "abc-123-token";

    private RestTemplate template;
    private MockRestServiceServer judge;
    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        template = new RestTemplate();
        judge = MockRestServiceServer.bindTo(template).ignoreExpectOrder(false).build();

        // A tiny budget and interval so the timeout test takes milliseconds rather
        // than the production 25 seconds.
        engine = new Judge0ExecutionEngine(template,
                new Judge0Properties(BASE, null, 2.0, 10.0, 256 * 1024, 64 * 1024,
                        1000, 2000, 5, 5, 200));
    }

    // ------------------------------------------------------------------ helpers

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** The create call: asserts wait=false, then hands back a token. */
    private void expectCreate() {
        judge.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true&wait=false"))
                .andExpect(method(HttpMethod.POST))
                // Limits are server-side and must be on every submission.
                .andExpect(jsonPath("$.cpu_time_limit").value(2.0))
                .andExpect(jsonPath("$.memory_limit").value(256 * 1024))
                .andExpect(jsonPath("$.enable_network").value(false))
                .andRespond(withSuccess("{\"token\":\"" + TOKEN + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    private String statusBody(int statusId, String stdout, String compileOutput, String stderr) {
        StringBuilder json = new StringBuilder("{\"status\":{\"id\":").append(statusId)
                .append("},\"time\":\"0.05\"");
        json.append(",\"stdout\":").append(stdout == null ? "null" : "\"" + b64(stdout) + "\"");
        json.append(",\"compile_output\":")
                .append(compileOutput == null ? "null" : "\"" + b64(compileOutput) + "\"");
        json.append(",\"stderr\":").append(stderr == null ? "null" : "\"" + b64(stderr) + "\"");
        return json.append("}").toString();
    }

    private void expectPoll(org.springframework.test.web.client.ExpectedCount count, String body) {
        judge.expect(count, requestTo(BASE + "/submissions/" + TOKEN
                        + "?base64_encoded=true&fields=*"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private ExecutionReport runOne(String expected) {
        return engine.execute(ArenaLanguage.PYTHON, "print(1)",
                List.of(new TestCase("in", expected)));
    }

    // ------------------------------------------------------------------ verdicts

    @Test
    @DisplayName("Accepted: correct output resolves on the first poll")
    void accepted() {
        expectCreate();
        expectPoll(once(), statusBody(3, "42\n", null, null));

        ExecutionReport report = runOne("42");

        assertThat(report.status()).isEqualTo(ExecutionStatus.ACCEPTED);
        assertThat(report.allPassed()).isTrue();
        assertThat(report.passedCount()).isEqualTo(1);
        judge.verify();
    }

    @Test
    @DisplayName("Wrong Answer: our comparator decides, not the judge's status")
    void wrongAnswer() {
        expectCreate();
        // Judge0 says Accepted (3) - it was given no expected_output to disagree
        // with. The verdict must still come from OutputComparator, which is what
        // keeps the documented comparison rules authoritative.
        expectPoll(once(), statusBody(3, "99\n", null, null));

        ExecutionReport report = runOne("42");

        assertThat(report.status()).isEqualTo(ExecutionStatus.WRONG_ANSWER);
        assertThat(report.allPassed()).isFalse();
        assertThat(report.cases().get(0).actualOutput()).isEqualTo("99\n");
        judge.verify();
    }

    @Test
    @DisplayName("Time Limit Exceeded is a verdict, reached by polling")
    void timeLimitExceeded() {
        expectCreate();
        expectPoll(once(), statusBody(5, null, null, null));

        ExecutionReport report = runOne("42");

        // The case the fix exists for: this used to surface as an outage.
        assertThat(report.status()).isEqualTo(ExecutionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(report.allPassed()).isFalse();
        judge.verify();
    }

    @Test
    @DisplayName("Compilation Error stops early and carries diagnostics")
    void compilationError() {
        expectCreate();
        expectPoll(once(), statusBody(6, null, "line 1: syntax error", null));

        // Two cases requested, but only one submission should ever be created: a
        // failed build fails every case identically.
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON, "def broken(",
                List.of(new TestCase("a", "1"), new TestCase("b", "2")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(report.compileOutput()).isEqualTo("line 1: syntax error");
        assertThat(report.cases()).isEmpty();
        judge.verify();
    }

    @Test
    @DisplayName("Runtime Error surfaces stderr")
    void runtimeError() {
        expectCreate();
        expectPoll(once(), statusBody(11, null, null, "ZeroDivisionError: division by zero"));

        ExecutionReport report = runOne("42");

        assertThat(report.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(report.cases().get(0).stderr()).contains("ZeroDivisionError");
        judge.verify();
    }

    @Test
    @DisplayName("every runtime-error variant 7-12 maps to RUNTIME_ERROR")
    void runtimeErrorVariants() {
        for (int statusId = 7; statusId <= 12; statusId++) {
            setUp();
            expectCreate();
            expectPoll(once(), statusBody(statusId, null, null, "boom"));

            assertThat(runOne("42").status())
                    .as("status %d", statusId)
                    .isEqualTo(ExecutionStatus.RUNTIME_ERROR);
            judge.verify();
        }
    }

    @Test
    @DisplayName("13 and 14 are infrastructure faults, not verdicts")
    void internalStatusesAreInternalErrors() {
        for (int statusId : new int[]{13, 14}) {
            setUp();
            expectCreate();
            expectPoll(once(), statusBody(statusId, null, null, null));

            // The service layer turns INTERNAL_ERROR into a 503, so it never reads as
            // a wrong answer.
            assertThat(runOne("42").status())
                    .as("status %d", statusId)
                    .isEqualTo(ExecutionStatus.INTERNAL_ERROR);
            judge.verify();
        }
    }

    // ------------------------------------------------------------------- polling

    @Test
    @DisplayName("In Queue and Processing keep polling until a terminal status")
    void queueAndProcessingContinuePolling() {
        expectCreate();
        // 1 -> 2 -> 2 -> 3. Only the last one is a verdict.
        expectPoll(once(), statusBody(1, null, null, null));
        expectPoll(once(), statusBody(2, null, null, null));
        expectPoll(once(), statusBody(2, null, null, null));
        expectPoll(once(), statusBody(3, "42\n", null, null));

        ExecutionReport report = runOne("42");

        assertThat(report.status()).isEqualTo(ExecutionStatus.ACCEPTED);
        // All four polls consumed, in order.
        judge.verify();
    }

    @Test
    @DisplayName("a submission that never resolves is abandoned, not waited on forever")
    void pollingIsBounded() {
        expectCreate();
        // Always Processing. Without a budget this would spin until the container
        // died, holding a Tomcat thread the whole time.
        expectPoll(manyTimes(), statusBody(2, null, null, null));

        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("did not return a result in time");

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        // The configured budget is 200ms here. Generous upper bound so the assertion
        // is about being bounded, not about scheduler precision.
        assertThat(elapsedMs).isLessThan(5_000L);
    }

    @Test
    @DisplayName("a poll timeout is never reported as a wrong answer")
    void pollTimeoutIsNotAVerdict() {
        expectCreate();
        expectPoll(manyTimes(), statusBody(1, null, null, null));

        // ExecutionUnavailableException, not an ExecutionReport. The distinction is
        // what stops an outage being scored against a participant.
        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class);
    }

    // ------------------------------------------------------------------ failures

    @Test
    @DisplayName("a failure creating the submission is an outage")
    void createFailureIsAnOutage() {
        judge.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true&wait=false"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("did not accept the submission");
    }

    @Test
    @DisplayName("a create that returns no token is an outage")
    void missingTokenIsAnOutage() {
        judge.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true&wait=false"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("no token");
    }

    @Test
    @DisplayName("a failure reading status is an outage")
    void pollFailureIsAnOutage() {
        expectCreate();
        judge.expect(once(), requestTo(BASE + "/submissions/" + TOKEN
                + "?base64_encoded=true&fields=*")).andRespond(withServerError());

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("did not respond");
    }

    @Test
    @DisplayName("a status response with no status field is an outage, not a guess")
    void statuslessResponseIsAnOutage() {
        expectCreate();
        expectPoll(once(), "{\"stdout\":null}");

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .hasMessageContaining("no status");
    }

    @Test
    @DisplayName("no outage message leaks the judge's host or port")
    void outageMessagesLeakNoInfrastructure() {
        expectCreate();
        judge.expect(once(), requestTo(BASE + "/submissions/" + TOKEN
                + "?base64_encoded=true&fields=*")).andRespond(withServerError());

        assertThatThrownBy(() -> runOne("42"))
                .isInstanceOf(ExecutionUnavailableException.class)
                .satisfies(thrown -> {
                    // The cause may carry the URL - it is logged, never shown. The
                    // message the service layer forwards must not.
                    assertThat(thrown.getMessage()).doesNotContain("judge.test");
                    assertThat(thrown.getMessage()).doesNotContain("2358");
                    assertThat(thrown.getMessage()).doesNotContain(TOKEN);
                });
    }

    // ------------------------------------------------------------- one per case

    @Test
    @DisplayName("each test case gets its own submission")
    void oneSubmissionPerCase() {
        for (int i = 0; i < 3; i++) {
            judge.expect(once(), requestTo(BASE + "/submissions?base64_encoded=true&wait=false"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"token\":\"" + TOKEN + "\"}",
                            MediaType.APPLICATION_JSON));
            expectPoll(once(), statusBody(3, "42\n", null, null));
        }

        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON, "print(42)",
                List.of(new TestCase("a", "42"), new TestCase("b", "42"),
                        new TestCase("c", "42")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.ACCEPTED);
        assertThat(report.totalCount()).isEqualTo(3);
        judge.verify();
    }

    @Test
    @DisplayName("empty source never reaches the judge at all")
    void emptySourceShortCircuits() {
        ExecutionReport report = engine.execute(
                ArenaLanguage.PYTHON, "   ", List.of(new TestCase("a", "1")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        // No expectations were set, so any HTTP call would have failed the test.
        judge.verify();
    }
}
