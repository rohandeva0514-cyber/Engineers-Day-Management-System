package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import in.mittechkernel.registration.FakeExecutionEngine.Mode;
import in.mittechkernel.registration.arena.execution.ExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase D: Run and Submit.
 *
 * <p>Runs against {@link FakeExecutionEngine}, so these are deterministic and need
 * no judge. What they prove is everything around execution: who may execute, what
 * comes back, what a submission does, and what an outage must not do. The compilers
 * themselves are covered by {@code Judge0IntegrationTest}.
 */
@org.springframework.test.context.TestPropertySource(
        properties = "app.arena.execution-enabled=true")
class ArenaExecutionApiTest extends PostgresIntegrationTest {

    /**
     * Replaces the real engine for this context.
     *
     * <p>{@code @Primary} rather than excluding the Judge0 bean, so the real engine
     * is still constructed and its configuration still has to bind - a broken
     * Judge0Properties would fail these tests too, which is worth knowing.
     */
    @TestConfiguration
    static class FakeEngineConfig {
        @Bean
        @Primary
        FakeExecutionEngine fakeExecutionEngine() {
            return new FakeExecutionEngine();
        }
    }

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    /** Everything that must never appear in an execution response. */
    private static final List<String> FORBIDDEN = List.of(
            "corrected_code", "correctedCode", "hidden_tests", "hiddenTests",
            "hiddenTestCount", "hidden_test_count", "why_wrong", "whyWrong",
            "bug_count", "bugCount", "\"bugs\"",
            // Judge0's own vocabulary and infrastructure must not surface either.
            "language_id", "languageId", "judge0", "2358", "submissions",
            "token", "cpu_time_limit", "memory_limit", "wall_time");

    @Autowired
    private FakeExecutionEngine fake;

    @Autowired
    private ExecutionEngine injectedEngine;

    private String code;

    @BeforeEach
    void setUp() {
        fake.reset();

        ResponseEntity<JsonNode> registration = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "EXE001", (short) 1),
                JsonNode.class);
        code = registration.getBody().get("registrations").get(0).get("accessCode").asText();
        setArena("ACTIVE");
    }

    // ------------------------------------------------------------------ helpers

    private void setArena(String status) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD).exchange(
                "/api/admin/arena/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"" + status + "\"}", h), JsonNode.class);
    }

    private String checkIn(String accessCode) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/arena/access", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"" + accessCode + "\"}", h), JsonNode.class)
                .getBody().get("sessionToken").asText();
    }

    private String missionIn(String language) {
        String token = checkIn(code);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        http.exchange("/api/arena/attempt/start", HttpMethod.POST,
                new HttpEntity<>("{\"language\":\"" + language + "\"}", h), JsonNode.class);
        return token;
    }

    private ResponseEntity<JsonNode> post(String token, String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<String> postRaw(String token, String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    private ResponseEntity<JsonNode> run(String token, String ref, String source) {
        return post(token, "/api/arena/problems/" + ref + "/run", json(source));
    }

    private ResponseEntity<JsonNode> submit(String token, String ref, String source) {
        return post(token, "/api/arena/problems/" + ref + "/submit", json(source));
    }

    private static String json(String source) {
        return "{\"code\":" + quote(source) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private String status(String ref) {
        return jdbc.queryForObject(
                "SELECT status FROM attempt_problem WHERE ref = ?", String.class, ref);
    }

    // ---------------------------------------------------------------- run: basics

    @Test
    @DisplayName("the fake engine is the one actually wired in")
    void fakeEngineIsInjected() {
        assertThat(injectedEngine.name()).isEqualTo("fake");
    }

    @Test
    @DisplayName("Run executes the visible tests and reports each one")
    void runReportsVisibleTests() {
        String token = missionIn("java");
        fake.setMode(Mode.PASS_ALL);

        ResponseEntity<JsonNode> response = run(token, "E-01", "class A {}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = response.getBody();
        assertThat(body.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(body.get("ref").asText()).isEqualTo("E-01");
        assertThat(body.get("tests")).isNotEmpty();
        assertThat(body.get("passedCount").asInt()).isEqualTo(body.get("totalCount").asInt());
        assertThat(body.get("runCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Run uses the VISIBLE test set, not the hidden one")
    void runUsesVisibleTestsOnly() {
        String token = missionIn("cpp");

        // The bank's visible sets are 2-3 cases; hidden are 5-7. Asserting the count
        // handed to the engine is how we know which set was used.
        int visibleCount = post(token, "/api/arena/problems/E-01/run", json("x"))
                .getBody().get("totalCount").asInt();

        JsonNode problem = http.exchange("/api/arena/problems/E-01", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class).getBody();

        assertThat(fake.lastTestCount()).isEqualTo(visibleCount);
        assertThat(visibleCount).isEqualTo(problem.get("visibleTests").size());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    @DisplayName("Run never marks a problem solved, even when every visible test passes")
    void runNeverSolves() {
        String token = missionIn("java");
        fake.setMode(Mode.PASS_ALL);

        run(token, "E-01", "class A {}");
        run(token, "E-01", "class A {}");

        // Passing the visible tests proves nothing about the hidden suite.
        assertThat(status("E-01")).isNotEqualTo("SOLVED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE solved_at IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE submitted_at IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("repeated failing runs carry no penalty")
    void failedRunsAreNotPenalised() {
        String token = missionIn("java");
        fake.setMode(Mode.WRONG_ANSWER);

        for (int i = 0; i < 5; i++) {
            assertThat(run(token, "E-01", "class A {}").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Counted for the admin roster, never scored against the participant, and
        // the problem is still open.
        assertThat(jdbc.queryForObject(
                "SELECT run_count FROM attempt_problem WHERE ref = 'E-01'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT submitted_at FROM attempt_problem WHERE ref = 'E-01'", Object.class))
                .isNull();
    }

    @ParameterizedTest(name = "{0} is reported as {1}")
    @CsvSource({
            "PASS_ALL,ACCEPTED",
            "WRONG_ANSWER,WRONG_ANSWER",
            "COMPILE_ERROR,COMPILATION_ERROR",
            "RUNTIME_ERROR,RUNTIME_ERROR",
            "TIMEOUT,TIME_LIMIT_EXCEEDED"
    })
    @DisplayName("every engine outcome maps to a stable participant-facing status")
    void statusesAreNormalised(Mode mode, String expected) {
        String token = missionIn("python");
        fake.setMode(mode);

        JsonNode body = run(token, "E-01", "print(1)").getBody();
        assertThat(body.get("status").asText()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a compilation error carries the compiler's own message")
    void compilationErrorCarriesDiagnostics() {
        String token = missionIn("c");
        fake.setMode(Mode.COMPILE_ERROR);

        JsonNode body = run(token, "E-01", "int main(){").getBody();

        assertThat(body.get("status").asText()).isEqualTo("COMPILATION_ERROR");
        assertThat(body.get("compileOutput").asText()).contains("expected ';'");
        assertThat(body.get("tests")).isEmpty();
    }

    // ------------------------------------------------------------ run: refusals

    @Test
    @DisplayName("Run needs a session")
    void runRequiresSession() {
        assertThat(run(null, "E-01", "x").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("a forged session cannot execute")
    void forgedSessionCannotExecute() {
        ResponseEntity<JsonNode> response = run("not-a-token", "E-01", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_SESSION_INVALID");
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("Run is refused before the mission starts")
    void runRefusedBeforeStart() {
        String token = checkIn(code);

        ResponseEntity<JsonNode> response = run(token, "E-01", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_NOT_STARTED");
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("Run is refused after the deadline")
    void runRefusedAfterExpiry() {
        String token = missionIn("java");
        jdbc.update("UPDATE arena_attempt SET started_at = now() - interval '46 minutes', "
                + "expires_at = now() - interval '1 minute'");

        ResponseEntity<JsonNode> response = run(token, "E-01", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_FINALIZED");

        // The deadline is the server's. Nothing was executed.
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("Run is refused once the attempt is finalized")
    void runRefusedWhenAttemptFinalized() {
        String token = missionIn("java");
        jdbc.update("UPDATE arena_attempt SET state = 'SUBMITTED', finalized_at = now()");

        assertThat(run(token, "E-01", "x").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("Run is refused while the arena is stopped")
    void runRefusedWhenArenaStopped() {
        String token = missionIn("java");
        setArena("OFFLINE");

        ResponseEntity<JsonNode> response = run(token, "E-01", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("a problem outside this mission cannot be executed")
    void unknownProblemCannotBeExecuted() {
        ResponseEntity<JsonNode> response = run(missionIn("java"), "E-99", "x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PROBLEM_NOT_FOUND");
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("empty source is refused without troubling the judge")
    void emptySourceIsRefused() {
        String token = missionIn("java");

        assertThat(post(token, "/api/arena/problems/E-01/run", "{}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(run(token, "E-01", "   ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fake.calls()).isZero();
    }

    @Test
    @DisplayName("oversized source is refused")
    void oversizedSourceIsRefused() {
        String token = missionIn("java");

        ResponseEntity<JsonNode> response = run(token, "E-01", "x".repeat(70_000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(fake.calls()).isZero();
    }

    // -------------------------------------------------- language is server-side

    @ParameterizedTest(name = "a {0} attempt executes as {0}")
    @CsvSource({"c,C", "cpp,CPP", "java,JAVA", "python,PYTHON", "javascript,JAVASCRIPT"})
    @DisplayName("the execution language comes from the attempt")
    void languageComesFromTheAttempt(String languageId, String expectedEnum) {
        String token = missionIn(languageId);

        run(token, "E-01", "source");

        assertThat(fake.lastLanguage().name()).isEqualTo(expectedEnum);
    }

    @Test
    @DisplayName("a client cannot override the language or the judge's parameters")
    void clientCannotOverrideExecutionParameters() {
        String token = missionIn("java");

        // Everything a hostile client might hope is honoured. ExecuteRequest has one
        // component, so Jackson discards all of it.
        ResponseEntity<JsonNode> response = post(token, "/api/arena/problems/E-01/run",
                "{\"code\":\"class A {}\","
                        + "\"language\":\"python\",\"language_id\":71,\"languageId\":71,"
                        + "\"cpu_time_limit\":600,\"memory_limit\":99999999,"
                        + "\"wall_time_limit\":600,\"attemptId\":999,\"participantId\":999,"
                        + "\"ref\":\"H-04\",\"tests\":[],\"expected_output\":\"anything\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Still Java, still E-01.
        assertThat(fake.lastLanguage().name()).isEqualTo("JAVA");
        assertThat(response.getBody().get("ref").asText()).isEqualTo("E-01");
    }

    // ------------------------------------------------------------------- submit

    @Test
    @DisplayName("Submit runs the hidden suite and can mark a problem solved")
    void submitCanSolve() {
        String token = missionIn("java");
        fake.setMode(Mode.PASS_ALL);

        ResponseEntity<JsonNode> response = submit(token, "E-01", "class Fixed {}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("solved").asBoolean()).isTrue();
        assertThat(body.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(body.get("problemStatus").asText()).isEqualTo("SOLVED");
        assertThat(body.get("submittedAt").asText()).isNotBlank();

        assertThat(status("E-01")).isEqualTo("SOLVED");
        assertThat(jdbc.queryForObject(
                "SELECT solved_at FROM attempt_problem WHERE ref='E-01'", Object.class))
                .isNotNull();
    }

    @Test
    @DisplayName("Submit uses the HIDDEN test set")
    void submitUsesHiddenTests() {
        String token = missionIn("cpp");

        int visible = run(token, "E-01", "x").getBody().get("totalCount").asInt();
        int visibleHandedOver = fake.lastTestCount();

        submit(token, "E-01", "x");
        int hiddenHandedOver = fake.lastTestCount();

        // The bank's hidden sets are larger than its visible ones for every problem,
        // so a different count is proof a different set was executed.
        assertThat(visibleHandedOver).isEqualTo(visible);
        assertThat(hiddenHandedOver).isGreaterThan(visibleHandedOver);
    }

    @Test
    @DisplayName("a failing submission locks the problem without solving it")
    void failingSubmitStillLocks() {
        String token = missionIn("java");
        fake.setMode(Mode.WRONG_ANSWER);

        JsonNode body = submit(token, "E-01", "class Broken {}").getBody();

        assertThat(body.get("solved").asBoolean()).isFalse();
        assertThat(body.get("status").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(status("E-01")).isEqualTo("ATTEMPTED");
        assertThat(jdbc.queryForObject(
                "SELECT submitted_at FROM attempt_problem WHERE ref='E-01'", Object.class))
                .isNotNull();
    }

    @Test
    @DisplayName("a problem can only be submitted once")
    void submitIsIrreversible() {
        String token = missionIn("java");
        fake.setMode(Mode.WRONG_ANSWER);
        submit(token, "E-01", "first");

        ResponseEntity<JsonNode> again = submit(token, "E-01", "second try");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("PROBLEM_ALREADY_SUBMITTED");
    }

    @Test
    @DisplayName("a submitted problem can no longer be run or edited")
    void submittedProblemIsReadOnly() {
        String token = missionIn("java");
        submit(token, "E-01", "final answer");

        assertThat(run(token, "E-01", "more").getBody().get("code").asText())
                .isEqualTo("PROBLEM_ALREADY_SUBMITTED");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        ResponseEntity<JsonNode> draft = http.exchange(
                "/api/arena/problems/E-01/draft", HttpMethod.PUT,
                new HttpEntity<>("{\"code\":\"sneaky\"}", h), JsonNode.class);

        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(draft.getBody().get("code").asText()).isEqualTo("PROBLEM_ALREADY_SUBMITTED");
    }

    @Test
    @DisplayName("submitting one problem leaves the other eleven open")
    void submitIsPerProblem() {
        String token = missionIn("java");
        submit(token, "E-01", "done");

        // This is NOT the end of the mission - that is Phase E.
        assertThat(run(token, "E-02", "still working").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM arena_attempt", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE submitted_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("the judged code becomes the code on record")
    void submittedCodeIsStored() {
        String token = missionIn("java");
        submit(token, "E-01", "class TheJudgedOne {}");

        assertThat(jdbc.queryForObject(
                "SELECT draft_code FROM attempt_problem WHERE ref='E-01'", String.class))
                .isEqualTo("class TheJudgedOne {}");
    }

    @Test
    @DisplayName("only a full hidden pass solves; a near miss does not")
    void onlyFullPassSolves() {
        String token = missionIn("java");
        fake.passIfSourceContains("CORRECT");

        assertThat(submit(token, "E-01", "class Nearly {}").getBody()
                .get("solved").asBoolean()).isFalse();
        assertThat(status("E-01")).isEqualTo("ATTEMPTED");

        // A different problem, this time with the marker.
        assertThat(submit(token, "E-02", "class CORRECT {}").getBody()
                .get("solved").asBoolean()).isTrue();
        assertThat(status("E-02")).isEqualTo("SOLVED");
    }

    @Test
    @DisplayName("the board reflects a solved problem")
    void boardShowsSolved() {
        String token = missionIn("java");
        submit(token, "M-01", "class A {}");

        JsonNode board = http.exchange("/api/arena/problems", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class).getBody();

        assertThat(board.get("solvedCount").asInt()).isEqualTo(1);
    }

    // -------------------------------------------- infrastructure vs code failure

    @Test
    @DisplayName("an unreachable judge is a 503, never a wrong answer")
    void outageIsNotAVerdict() {
        String token = missionIn("java");
        fake.setMode(Mode.UNAVAILABLE);

        ResponseEntity<JsonNode> response = run(token, "E-01", "class A {}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code").asText()).isEqualTo("EXECUTION_UNAVAILABLE");

        // Nothing recorded, nothing changed.
        assertThat(status("E-01")).isEqualTo("NOT_ATTEMPTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_run", Integer.class)).isZero();
    }

    @Test
    @DisplayName("an outage during Submit does not consume the submission")
    void outageDoesNotConsumeSubmission() {
        String token = missionIn("java");
        fake.setMode(Mode.UNAVAILABLE);

        assertThat(submit(token, "E-01", "class A {}").getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // The problem is untouched, so the participant can try again once the judge
        // is back - an outage must not cost someone their one submission.
        assertThat(jdbc.queryForObject(
                "SELECT submitted_at FROM attempt_problem WHERE ref='E-01'", Object.class))
                .isNull();

        fake.setMode(Mode.PASS_ALL);
        assertThat(submit(token, "E-01", "class A {}").getBody().get("solved").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("a judge internal error is treated as an outage, not a failure")
    void internalErrorIsAnOutage() {
        String token = missionIn("java");
        fake.setMode(Mode.INTERNAL_ERROR);

        ResponseEntity<JsonNode> response = run(token, "E-01", "class A {}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status("E-01")).isEqualTo("NOT_ATTEMPTED");
    }

    @Test
    @DisplayName("an outage response leaks no infrastructure detail")
    void outageLeaksNothing() {
        String token = missionIn("java");
        fake.setMode(Mode.UNAVAILABLE);

        String body = postRaw(token, "/api/arena/problems/E-01/run", json("class A {}")).getBody();

        assertThat(body).doesNotContain("2358").doesNotContain("judge")
                .doesNotContain("localhost").doesNotContain("Exception")
                .doesNotContain("at in.mittechkernel");
    }

    // --------------------------------------------------------------- leakage

    @Test
    @DisplayName("no execution response leaks answer material or judge internals")
    void executionResponsesLeakNothing() {
        String token = missionIn("java");
        fake.setMode(Mode.WRONG_ANSWER);

        List<String> payloads = List.of(
                postRaw(token, "/api/arena/problems/E-01/run", json("class A {}")).getBody(),
                postRaw(token, "/api/arena/problems/E-01/submit", json("class A {}")).getBody());

        for (String payload : payloads) {
            assertThat(payload).isNotBlank();
            for (String forbidden : FORBIDDEN) {
                assertThat(payload)
                        .as("execution response must not contain %s", forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("a submission response describes nothing about the hidden suite")
    void submitDescribesNoHiddenShape() {
        String token = missionIn("python");
        fake.setMode(Mode.WRONG_ANSWER);

        JsonNode body = submit(token, "H-01", "print(1)").getBody();

        // A boolean and a verdict. No test list, no counts, no ratio - a participant
        // must not learn how large the hidden suite is or how much of it they failed.
        assertThat(body.has("tests")).isFalse();
        assertThat(body.has("passedCount")).isFalse();
        assertThat(body.has("totalCount")).isFalse();
        assertThat(List.copyOf(body.properties().stream().map(java.util.Map.Entry::getKey).toList()))
                .containsExactlyInAnyOrder("serverTime", "ref", "status", "solved",
                        "submittedAt", "compileOutput", "problemStatus");
    }

    @Test
    @DisplayName("a Run response echoes only the visible cases the participant already has")
    void runEchoesOnlyVisibleCases() {
        String token = missionIn("java");
        fake.setMode(Mode.PASS_ALL);

        JsonNode problem = http.exchange("/api/arena/problems/E-01", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class).getBody();
        JsonNode result = run(token, "E-01", "class A {}").getBody();

        assertThat(result.get("tests").size()).isEqualTo(problem.get("visibleTests").size());
    }

    // ------------------------------------------------------------ isolation

    @Test
    @DisplayName("one participant cannot execute against another's problem")
    void participantsCannotReachEachOther() {
        String mine = missionIn("java");
        submit(mine, "E-01", "mine");

        ResponseEntity<JsonNode> other = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "EXE002", (short) 2),
                JsonNode.class);
        String theirToken = checkIn(
                other.getBody().get("registrations").get(0).get("accessCode").asText());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(theirToken);
        http.exchange("/api/arena/attempt/start", HttpMethod.POST,
                new HttpEntity<>("{\"language\":\"python\"}", h), JsonNode.class);

        // Same handle, their own mission: unaffected by my submission.
        assertThat(run(theirToken, "E-01", "print(1)").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fake.lastLanguage().name()).isEqualTo("PYTHON");

        Integer submitted = jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE submitted_at IS NOT NULL",
                Integer.class);
        assertThat(submitted).isEqualTo(1);
    }

    @Test
    @DisplayName("a session taken over by another device keeps working for the new one")
    void sessionTakeoverPreservesExecution() {
        String first = missionIn("java");
        run(first, "E-01", "class A {}");

        String second = checkIn(code);

        assertThat(run(second, "E-01", "class A {}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run(first, "E-01", "class A {}").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------- concurrency

    @Test
    @DisplayName("concurrent executions from different participants stay independent")
    void concurrentExecutionsAreIndependent() throws Exception {
        // Eight separate participants, each with their own attempt and language,
        // all executing at once. There must be no shared "current submission".
        List<String> languages = List.of("c", "cpp", "java", "python", "javascript",
                "java", "python", "cpp");

        List<String> tokens = new java.util.ArrayList<>();
        for (int i = 0; i < languages.size(); i++) {
            ResponseEntity<JsonNode> reg = http.postForEntity("/api/registrations",
                    TestRequests.solo("debugging", "CON%03d".formatted(i), (short) 1),
                    JsonNode.class);
            String token = checkIn(
                    reg.getBody().get("registrations").get(0).get("accessCode").asText());
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.setBearerAuth(token);
            http.exchange("/api/arena/attempt/start", HttpMethod.POST,
                    new HttpEntity<>("{\"language\":\"" + languages.get(i) + "\"}", h),
                    JsonNode.class);
            tokens.add(token);
        }

        ExecutorService pool = Executors.newFixedThreadPool(languages.size());
        try {
            List<Callable<HttpStatus>> jobs = IntStream.range(0, tokens.size())
                    .<Callable<HttpStatus>>mapToObj(i -> () ->
                            (HttpStatus) run(tokens.get(i), "E-01", "source " + i).getStatusCode())
                    .toList();

            for (Future<HttpStatus> future : pool.invokeAll(jobs)) {
                assertThat(future.get()).isEqualTo(HttpStatus.OK);
            }
        } finally {
            pool.shutdownNow();
        }

        // Every participant's run is recorded against their own problem row.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_run", Integer.class))
                .isEqualTo(tokens.size());
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT attempt_problem_id) FROM arena_run", Integer.class))
                .isEqualTo(tokens.size());
    }

    // ----------------------------------------------------------- persistence

    @Test
    @DisplayName("runs are recorded without storing source or test content")
    void runsAreRecordedWithoutContent() {
        String token = missionIn("java");
        fake.setMode(Mode.PASS_ALL);
        run(token, "E-01", "class VerySpecificMarker {}");
        submit(token, "E-01", "class VerySpecificMarker {}");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_run", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT kind FROM arena_run ORDER BY id")
                .stream().map(row -> row.get("kind")).toList())
                .containsExactly("RUN", "SUBMIT");

        // arena_run records the shape of a result, never its content. The source
        // lives once, on attempt_problem.
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'arena_run'", String.class);
        assertThat(columns).doesNotContain("source_code", "stdout", "stderr", "expected_output");
    }
}
