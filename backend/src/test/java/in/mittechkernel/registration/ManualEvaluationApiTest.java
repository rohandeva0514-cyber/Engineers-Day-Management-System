package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Emergency manual-evaluation mode.
 *
 * <p>The event now runs without live execution: participants edit, autosave, and
 * submit their mission once; organisers read the saved code and award points by hand.
 * These tests cover that path end to end, plus the refusals that make it safe.
 *
 * <p>{@code execution-enabled=false} is set explicitly rather than inherited, so this
 * class asserts the mode it is named after regardless of what the default becomes
 * later.
 */
@TestPropertySource(properties = "app.arena.execution-enabled=false")
class ManualEvaluationApiTest extends PostgresIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    /** Answer material that must never appear in a participant response. */
    private static final List<String> FORBIDDEN = List.of(
            "corrected_code", "correctedCode", "hidden_tests", "hiddenTests",
            "hiddenTestCount", "why_wrong", "whyWrong", "bug_count", "bugCount",
            "\"bugs\"", "score", "solvedCount", "awardedPoints", "verdict");

    private String code;

    @BeforeEach
    void setUp() {
        ResponseEntity<JsonNode> registration = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "MAN001", (short) 1),
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

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private static HttpHeaders bearerJson(String token) {
        HttpHeaders h = bearer(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String checkIn() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/arena/access", HttpMethod.POST,
                        new HttpEntity<>("{\"code\":\"" + code + "\"}", h), JsonNode.class)
                .getBody().get("sessionToken").asText();
    }

    private String mission(String language) {
        String token = checkIn();
        http.exchange("/api/arena/attempt/start", HttpMethod.POST,
                new HttpEntity<>("{\"language\":\"" + language + "\"}", bearerJson(token)),
                JsonNode.class);
        return token;
    }

    private ResponseEntity<JsonNode> saveDraft(String token, String ref, String source) {
        return http.exchange("/api/arena/problems/" + ref + "/draft", HttpMethod.PUT,
                new HttpEntity<>("{\"code\":\"" + source + "\"}", bearerJson(token)),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> submitMission(String token) {
        return http.exchange("/api/arena/attempt/submit", HttpMethod.POST,
                new HttpEntity<>(token == null ? new HttpHeaders() : bearerJson(token)),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> admin(String path) {
        return http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD)
                .getForEntity(path, JsonNode.class);
    }

    private long attemptId() {
        Long id = jdbc.queryForObject("SELECT id FROM arena_attempt", Long.class);
        return id == null ? 0L : id;
    }

    // -------------------------------------------------- execution is disabled

    @Test
    @DisplayName("Run Code is refused server-side, not merely hidden in the UI")
    void runIsDisabled() {
        String token = mission("java");

        ResponseEntity<JsonNode> response = http.exchange(
                "/api/arena/problems/E-01/run", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"class A {}\"}", bearerJson(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("EXECUTION_DISABLED");

        // And no execution was recorded, so nothing reached the judge.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_run", Integer.class)).isZero();
    }

    @Test
    @DisplayName("the per-problem judge submit is refused too")
    void perProblemSubmitIsDisabled() {
        String token = mission("java");

        ResponseEntity<JsonNode> response = http.exchange(
                "/api/arena/problems/E-01/submit", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"class A {}\"}", bearerJson(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("EXECUTION_DISABLED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE submitted_at IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("the arena still works for everything that is not execution")
    void theArenaStillWorks() {
        String token = mission("python");

        // Board, navigation, problem detail and autosave are all untouched.
        assertThat(http.exchange("/api/arena/problems", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        for (String ref : List.of("H-04", "E-01", "M-02")) {
            assertThat(http.exchange("/api/arena/problems/" + ref, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), JsonNode.class).getStatusCode())
                    .as("opening %s", ref).isEqualTo(HttpStatus.OK);
        }
        assertThat(saveDraft(token, "E-01", "print(1)").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------- mission submission

    @Test
    @DisplayName("a participant can submit their mission")
    void participantCanSubmit() {
        String token = mission("java");
        saveDraft(token, "E-01", "class Answer {}");

        ResponseEntity<JsonNode> response = submitMission(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("attempt").get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("the final submission timestamp is the server's")
    void timestampIsServerSide() {
        Instant before = Instant.now();
        String token = mission("java");
        submitMission(token);
        Instant after = Instant.now();

        Instant finalizedAt = jdbc.queryForObject(
                "SELECT finalized_at FROM arena_attempt", Instant.class);

        assertThat(finalizedAt).isNotNull();
        assertThat(finalizedAt).isBetween(before.minusSeconds(5), after.plusSeconds(5));
        assertThat(jdbc.queryForObject(
                "SELECT final_state_reason FROM arena_attempt", String.class))
                .isEqualTo("PARTICIPANT");
    }

    @Test
    @DisplayName("a client cannot supply its own submission timestamp")
    void clientCannotSetTimestamp() {
        String token = mission("java");

        // The endpoint takes no body at all; anything sent is discarded.
        ResponseEntity<JsonNode> response = http.exchange(
                "/api/arena/attempt/submit", HttpMethod.POST,
                new HttpEntity<>("{\"finalizedAt\":\"2020-01-01T00:00:00Z\",\"score\":2400}",
                        bearerJson(token)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Instant finalizedAt = jdbc.queryForObject(
                "SELECT finalized_at FROM arena_attempt", Instant.class);
        assertThat(finalizedAt).isAfter(Instant.parse("2025-01-01T00:00:00Z"));

        // And no score came in with it - scoring is an organiser's act.
        assertThat(jdbc.queryForObject("SELECT score FROM arena_attempt", Integer.class)).isNull();
    }

    @Test
    @DisplayName("a submitted attempt cannot be edited")
    void submittedAttemptIsReadOnly() {
        String token = mission("java");
        saveDraft(token, "E-01", "final answer");
        submitMission(token);

        ResponseEntity<JsonNode> draft = saveDraft(token, "E-01", "sneaky change");

        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(draft.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_FINALIZED");

        // The stored code is still what was submitted.
        assertThat(jdbc.queryForObject(
                "SELECT draft_code FROM attempt_problem WHERE ref='E-01'", String.class))
                .isEqualTo("final answer");
    }

    @Test
    @DisplayName("a mission can only be submitted once")
    void submissionIsIrreversible() {
        String token = mission("java");
        submitMission(token);
        Instant first = jdbc.queryForObject(
                "SELECT finalized_at FROM arena_attempt", Instant.class);

        ResponseEntity<JsonNode> again = submitMission(token);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_FINALIZED");

        // The original timestamp is untouched - it is the tiebreak.
        assertThat(jdbc.queryForObject("SELECT finalized_at FROM arena_attempt", Instant.class))
                .isEqualTo(first);
    }

    @Test
    @DisplayName("an unauthenticated caller cannot submit")
    void unauthenticatedCannotSubmit() {
        mission("java");

        assertThat(submitMission(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a stale session cannot submit")
    void staleSessionCannotSubmit() {
        String first = mission("java");
        String second = checkIn();   // takeover; the first token is now dead

        ResponseEntity<JsonNode> stale = submitMission(first);

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(stale.getBody().get("code").asText()).isEqualTo("ARENA_SESSION_INVALID");

        // The live session still can - takeover behaviour is unchanged.
        assertThat(submitMission(second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a forged session cannot submit")
    void forgedSessionCannotSubmit() {
        mission("java");
        assertThat(submitMission("not-a-real-token").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a mission that has not started cannot be submitted")
    void unstartedMissionCannotSubmit() {
        String token = checkIn();

        ResponseEntity<JsonNode> response = submitMission(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_NOT_STARTED");
    }

    @Test
    @DisplayName("submission is refused once the deadline has passed")
    void lateSubmissionIsRefused() {
        String token = mission("java");
        jdbc.update("UPDATE arena_attempt SET started_at = now() - interval '46 minutes', "
                + "expires_at = now() - interval '1 minute'");

        ResponseEntity<JsonNode> response = submitMission(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("submission survives a reload")
    void submissionSurvivesReload() {
        String token = mission("java");
        submitMission(token);

        // What a refresh does: same token, one state call.
        JsonNode attempt = http.exchange("/api/arena/attempt", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class).getBody().get("attempt");

        assertThat(attempt.get("state").asText()).isEqualTo("SUBMITTED");

        // And re-entering the access code lands on the same finished state.
        JsonNode resumed = http.exchange("/api/arena/access", HttpMethod.POST,
                        new HttpEntity<>("{\"code\":\"" + code + "\"}", bearerJson(token)),
                        JsonNode.class)
                .getBody().get("attempt");
        assertThat(resumed.get("state").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("one registration still yields exactly one attempt")
    void oneAttemptInvariantHolds() {
        String token = mission("java");
        submitMission(token);

        for (int i = 0; i < 3; i++) {
            checkIn();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_attempt", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the participant response after submission carries no hidden content")
    void participantResponseLeaksNothing() {
        String token = mission("java");
        saveDraft(token, "E-01", "class A {}");

        ResponseEntity<String> raw = http.exchange("/api/arena/attempt/submit", HttpMethod.POST,
                new HttpEntity<>(bearerJson(token)), String.class);

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String forbidden : FORBIDDEN) {
            assertThat(raw.getBody())
                    .as("submission response must not contain %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    // -------------------------------------------------------------- admin view

    @Test
    @DisplayName("an organiser sees the submission and the participant's code")
    void adminSeesSubmittedCode() {
        String token = mission("python");
        saveDraft(token, "E-01", "print(sum(marks)/3)");
        saveDraft(token, "M-01", "print('medium attempt')");
        submitMission(token);

        JsonNode list = admin("/api/admin/arena/submissions").getBody();
        assertThat(list.get("total").asInt()).isEqualTo(1);

        JsonNode row = list.get("submissions").get(0);
        assertThat(row.get("fullName").asText()).isEqualTo("Student MAN001");
        assertThat(row.get("rollNo").asText()).isEqualTo("MAN001");
        assertThat(row.get("email").asText()).contains("man001");
        assertThat(row.get("branch").asText()).isEqualTo("Computer Engineering");
        assertThat(row.get("division").asText()).isEqualTo("A");
        assertThat(row.get("yearLevel").asInt()).isEqualTo(1);
        assertThat(row.get("language").asText()).isEqualTo("python");
        assertThat(row.get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(row.get("finalSubmittedAt").asText()).isNotBlank();
        assertThat(row.get("evaluationStatus").asText()).isEqualTo("PENDING");

        JsonNode detail = admin("/api/admin/arena/submissions/" + attemptId()).getBody();
        assertThat(detail.get("problems")).hasSize(12);

        JsonNode easy = detail.get("problems").get(0);
        assertThat(easy.get("ref").asText()).isEqualTo("E-01");
        assertThat(easy.get("title").asText()).isNotBlank();
        assertThat(easy.get("difficulty").asText()).isEqualTo("EASY");
        assertThat(easy.get("points").asInt()).isEqualTo(100);
        assertThat(easy.get("sourceCode").asText()).isEqualTo("print(sum(marks)/3)");
        assertThat(easy.get("awardedPoints").isNull()).isTrue();
    }

    @Test
    @DisplayName("the code an organiser sees is the latest autosaved version")
    void adminSeesLatestAutosave() {
        String token = mission("python");
        saveDraft(token, "E-01", "first attempt");
        saveDraft(token, "E-01", "second attempt");
        saveDraft(token, "E-01", "final version");
        submitMission(token);

        JsonNode detail = admin("/api/admin/arena/submissions/" + attemptId()).getBody();
        JsonNode easy = detail.get("problems").get(0);

        assertThat(easy.get("sourceCode").asText()).isEqualTo("final version");
    }

    @Test
    @DisplayName("a participant who ran out of time is still listed for evaluation")
    void expiredAttemptsAreStillEvaluated() {
        String token = mission("java");
        saveDraft(token, "E-01", "ran out of time");
        jdbc.update("UPDATE arena_attempt SET started_at = now() - interval '46 minutes', "
                + "expires_at = now() - interval '1 minute'");

        JsonNode list = admin("/api/admin/arena/submissions").getBody();

        // Their code exists and has to be marked; excluding them would lose a
        // student's work to a technicality.
        assertThat(list.get("total").asInt()).isEqualTo(1);
        JsonNode row = list.get("submissions").get(0);
        assertThat(row.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(row.get("finalSubmittedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("a non-admin cannot reach any submission data")
    void nonAdminCannotReachSubmissions() {
        String token = mission("java");
        submitMission(token);
        long id = attemptId();

        // No credentials.
        assertThat(http.getForEntity("/api/admin/arena/submissions", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/admin/arena/submissions/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/admin/arena/submissions/export.csv", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Wrong password.
        assertThat(http.withBasicAuth(ADMIN_USER, "wrong")
                .getForEntity("/api/admin/arena/submissions", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // An arena participant's own bearer is not admin authorisation either.
        assertThat(http.exchange("/api/admin/arena/submissions", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------------------------------------------------------- admin scoring

    @Test
    @DisplayName("an organiser awards full points or nothing, and the total follows")
    void adminAwardsPoints() {
        String token = mission("java");
        submitMission(token);
        long id = attemptId();

        award(id, "E-01", 100);
        assertThat(jdbc.queryForObject("SELECT score FROM arena_attempt", Integer.class))
                .isEqualTo(100);

        award(id, "M-01", 200);
        award(id, "H-01", 300);
        assertThat(jdbc.queryForObject("SELECT score FROM arena_attempt", Integer.class))
                .isEqualTo(600);

        // Zero is a judgement, not an absence.
        award(id, "E-02", 0);
        assertThat(jdbc.queryForObject("SELECT score FROM arena_attempt", Integer.class))
                .isEqualTo(600);

        JsonNode row = admin("/api/admin/arena/submissions").getBody().get("submissions").get(0);
        assertThat(row.get("totalScore").asInt()).isEqualTo(600);
        assertThat(row.get("evaluationStatus").asText()).isEqualTo("PARTIAL");
        assertThat(row.get("problemsEvaluated").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("partial credit is refused")
    void partialCreditIsRefused() {
        String token = mission("java");
        submitMission(token);

        ResponseEntity<JsonNode> response = awardRaw(attemptId(), "E-01", 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT awarded_points FROM attempt_problem WHERE ref='E-01'", Integer.class))
                .isNull();
    }

    @Test
    @DisplayName("an award can be cleared")
    void awardCanBeCleared() {
        String token = mission("java");
        submitMission(token);
        long id = attemptId();

        award(id, "E-01", 100);
        awardRaw(id, "E-01", null);

        assertThat(jdbc.queryForObject(
                "SELECT awarded_points FROM attempt_problem WHERE ref='E-01'", Integer.class))
                .isNull();
        // Back to nothing evaluated, so the total returns to null rather than 0.
        assertThat(jdbc.queryForObject("SELECT score FROM arena_attempt", Integer.class)).isNull();
    }

    @Test
    @DisplayName("awarding does not mark a problem SOLVED")
    void awardingIsNotSolving() {
        String token = mission("java");
        submitMission(token);
        award(attemptId(), "E-01", 100);

        // SOLVED means a judge verified it. Nothing ran, so nothing is solved - and
        // an organiser's award must stay distinguishable from a verified pass.
        assertThat(jdbc.queryForObject(
                "SELECT status FROM attempt_problem WHERE ref='E-01'", String.class))
                .isNotEqualTo("SOLVED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE solved_at IS NOT NULL",
                Integer.class)).isZero();
    }

    private void award(long attemptId, String ref, Integer points) {
        assertThat(awardRaw(attemptId, ref, points).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> awardRaw(long attemptId, String ref, Integer points) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD).exchange(
                "/api/admin/arena/submissions/" + attemptId + "/problems/" + ref + "/award",
                HttpMethod.PUT,
                new HttpEntity<>("{\"points\":" + points + "}", h), JsonNode.class);
    }

    // ----------------------------------------------------------------- export

    @Test
    @DisplayName("the CSV export carries everything needed to mark the event offline")
    void exportCarriesEverything() {
        String token = mission("python");
        saveDraft(token, "E-01", "print('line one')");
        submitMission(token);
        award(attemptId(), "E-01", 100);

        ResponseEntity<String> csv = http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD)
                .getForEntity("/api/admin/arena/submissions/export.csv", String.class);

        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = csv.getBody();

        assertThat(body).contains("full_name", "roll_no", "language",
                "final_submitted_at", "problem_ref", "difficulty", "points",
                "awarded_points", "source_code");
        assertThat(body).contains("Student MAN001").contains("MAN001")
                .contains("python").contains("print('line one')");

        // One row per problem, plus a header.
        assertThat(body.split("\r\n")).hasSize(13);

        // Never answer material, even for an organiser - it lives in the bank on disk.
        assertThat(body).doesNotContain("corrected_code").doesNotContain("hidden_tests")
                .doesNotContain("why_wrong");
    }

    @Test
    @DisplayName("source code containing commas, quotes and newlines survives the CSV")
    void exportQuotesHostileSource() {
        String token = mission("python");
        // The hostile case for CSV, which is also just ordinary code.
        saveDraft(token, "E-01", "a = [1, 2, 3]\\nprint(\\\"hi, there\\\")");
        submitMission(token);

        String body = http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD)
                .getForEntity("/api/admin/arena/submissions/export.csv", String.class).getBody();

        // Embedded quotes doubled, per RFC 4180, so the columns do not shift.
        assertThat(body).contains("\"\"hi, there\"\"");
        // A UTF-8 BOM, so Excel does not mangle non-ASCII names.
        assertThat(body).startsWith("﻿");
    }
}
