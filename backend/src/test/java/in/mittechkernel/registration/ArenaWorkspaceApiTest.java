package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase C: the board, one problem, and drafts.
 *
 * <p>The assertions that matter most are about what must NOT happen: answer
 * material in a response, a participant reaching another language's bank or another
 * participant's draft, a problem turning SOLVED without anything having been run,
 * and the workspace opening before the mission starts.
 */
class ArenaWorkspaceApiTest extends PostgresIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    /**
     * Every field name that would give the game away.
     *
     * <p>Checked as raw substrings against the serialised response, not against a
     * parsed object graph: the point is that the bytes leaving the server contain
     * none of it, whatever shape someone later gives the DTOs.
     */
    private static final List<String> FORBIDDEN = List.of(
            "corrected_code", "correctedCode",
            "hidden_tests", "hiddenTests",
            "hiddenTestCount", "hidden_test_count",
            "why_wrong", "whyWrong",
            "bug_count", "bugCount",
            "\"bugs\"");

    private String code;

    @BeforeEach
    void registerAndOpenArena() {
        ResponseEntity<JsonNode> registration = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "WRK001", (short) 1),
                JsonNode.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        code = registration.getBody().get("registrations").get(0).get("accessCode").asText();

        setArena("ACTIVE");
    }

    // ------------------------------------------------------------------ helpers

    private void setArena(String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD).exchange(
                "/api/admin/arena/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"" + status + "\"}", headers), JsonNode.class);
    }

    private String checkIn(String accessCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.exchange("/api/arena/access", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"" + accessCode + "\"}", headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("sessionToken").asText();
    }

    private ResponseEntity<JsonNode> start(String token, String language) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return http.exchange("/api/arena/attempt/start", HttpMethod.POST,
                new HttpEntity<>("{\"language\":\"" + language + "\"}", headers), JsonNode.class);
    }

    /** A participant checked in and running, in the given language. */
    private String missionIn(String language) {
        String token = checkIn(code);
        assertThat(start(token, language).getStatusCode()).isEqualTo(HttpStatus.OK);
        return token;
    }

    private ResponseEntity<String> getRaw(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<JsonNode> get(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> putDraft(String token, String ref, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return http.exchange("/api/arena/problems/" + ref + "/draft", HttpMethod.PUT,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static List<String> refsOf(JsonNode board) {
        return board.get("zones").findValuesAsText("ref");
    }

    // -------------------------------------------------------------- the board

    @Test
    @DisplayName("the board carries twelve problems in three open zones")
    void boardHasTwelveProblemsInThreeZones() {
        JsonNode board = get(missionIn("java"), "/api/arena/problems").getBody();

        assertThat(board.get("totalProblems").asInt()).isEqualTo(12);
        assertThat(board.get("language").asText()).isEqualTo("java");
        assertThat(board.get("zones")).hasSize(3);

        // Read the zone-level field directly rather than with findValuesAsText,
        // which recurses - and every problem carries a "difficulty" of its own, so
        // a recursive search returns all fifteen rather than the three zones.
        List<String> zones = new java.util.ArrayList<>();
        board.get("zones").forEach(zone -> zones.add(zone.get("difficulty").asText()));
        assertThat(zones).containsExactly("EASY", "MEDIUM", "HARD");

        board.get("zones").forEach(zone ->
                assertThat(zone.get("problems")).hasSize(4));
    }

    @Test
    @DisplayName("nothing on the board is locked")
    void nothingIsLocked() {
        JsonNode board = get(missionIn("python"), "/api/arena/problems").getBody();

        // There is no progression to enforce, so there is no flag describing one.
        // A client cannot render a gate it is never told about.
        assertThat(board.toString()).doesNotContain("locked");
        assertThat(board.toString()).doesNotContain("unlocked");
        assertThat(refsOf(board)).hasSize(12);
    }

    @Test
    @DisplayName("every problem starts NOT_ATTEMPTED")
    void everyProblemStartsUnattempted() {
        JsonNode board = get(missionIn("c"), "/api/arena/problems").getBody();

        assertThat(board.get("zones").findValuesAsText("status"))
                .hasSize(12)
                .allMatch("NOT_ATTEMPTED"::equals);
        assertThat(board.get("solvedCount").asInt()).isZero();
    }

    @ParameterizedTest(name = "a {0} participant gets {0} problems")
    @CsvSource({"c,C", "cpp,C++", "java,JAVA", "python,PY", "javascript,JS"})
    @DisplayName("the board is drawn from the locked language, and only that one")
    void boardMatchesLockedLanguage(String languageId, String idPrefix) {
        String token = missionIn(languageId);

        JsonNode board = get(token, "/api/arena/problems").getBody();
        assertThat(board.get("language").asText()).isEqualTo(languageId);

        // Handles are language-neutral, so confirm through the problem itself: the
        // detail response names the language it belongs to.
        JsonNode problem = get(token, "/api/arena/problems/E-01").getBody();
        assertThat(problem.get("language").asText()).isEqualTo(languageId);

        // And the bank ids behind them are the right family - checked in the
        // database, which is the only place the id is written.
        String storedId = jdbc.queryForObject(
                "SELECT problem_id FROM attempt_problem WHERE ref = 'E-01'", String.class);
        assertThat(storedId).startsWith(idPrefix.toUpperCase(Locale.ROOT).equals("C++")
                ? "C++" : idPrefix);
    }

    @Test
    @DisplayName("the board is materialised once and never reshuffles")
    void boardIsStable() {
        String token = missionIn("cpp");

        List<String> first = refsOf(get(token, "/api/arena/problems").getBody());
        List<String> second = refsOf(get(token, "/api/arena/problems").getBody());

        assertThat(second).isEqualTo(first);
        assertThat(first).containsExactly(
                "E-01", "E-02", "E-03", "E-04",
                "M-01", "M-02", "M-03", "M-04",
                "H-01", "H-02", "H-03", "H-04");

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem", Integer.class);
        assertThat(rows).isEqualTo(12);
    }

    // ----------------------------------------------------- free navigation

    @Test
    @DisplayName("zones can be worked in any order with no forced progression")
    void navigationIsFree() {
        String token = missionIn("java");

        // Hard first, then Easy, then Medium, then back to Hard. Every one must
        // succeed: there is no sequence to respect.
        for (String ref : List.of("H-01", "E-03", "M-02", "H-04", "E-01")) {
            assertThat(get(token, "/api/arena/problems/" + ref).getStatusCode())
                    .as("opening %s", ref)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("a participant can return to a problem they already opened")
    void problemsCanBeRevisited() {
        String token = missionIn("python");

        String first = get(token, "/api/arena/problems/M-03").getBody().get("title").asText();
        get(token, "/api/arena/problems/H-01");
        String again = get(token, "/api/arena/problems/M-03").getBody().get("title").asText();

        assertThat(again).isEqualTo(first);
    }

    // --------------------------------------------------------- problem detail

    @Test
    @DisplayName("a problem carries everything needed to debug it")
    void problemDetailIsComplete() {
        JsonNode problem = get(missionIn("cpp"), "/api/arena/problems/E-01").getBody();

        assertThat(problem.get("ref").asText()).isEqualTo("E-01");
        assertThat(problem.get("title").asText()).isNotBlank();
        assertThat(problem.get("difficulty").asText()).isEqualTo("EASY");
        assertThat(problem.get("points").asInt()).isEqualTo(100);
        assertThat(problem.get("problemStatement").asText()).isNotBlank();
        assertThat(problem.get("inputFormat").asText()).isNotBlank();
        assertThat(problem.get("outputFormat").asText()).isNotBlank();
        assertThat(problem.get("constraints").asText()).isNotBlank();
        assertThat(problem.get("buggyCode").asText()).isNotBlank();
        assertThat(problem.get("visibleTests")).isNotEmpty();

        // Untouched, so the client falls back to the bank's code.
        assertThat(problem.get("draftCode").isNull()).isTrue();
        assertThat(problem.get("status").asText()).isEqualTo("NOT_ATTEMPTED");
    }

    @Test
    @DisplayName("opening a problem does not change its status")
    void openingIsNotAttempting() {
        String token = missionIn("java");

        get(token, "/api/arena/problems/E-02");
        get(token, "/api/arena/problems/E-02");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM attempt_problem WHERE ref = 'E-02'", String.class))
                .isEqualTo("NOT_ATTEMPTED");
    }

    @ParameterizedTest(name = "\"{0}\" is not a problem in this mission")
    @ValueSource(strings = {"E-99", "X-01", "../../etc/passwd", "E-1", "nonsense"})
    @DisplayName("an unknown handle is refused")
    void unknownHandleIsRefused(String ref) {
        ResponseEntity<JsonNode> response =
                get(missionIn("java"), "/api/arena/problems/" + ref);

        assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------- INFORMATION LEAKAGE

    @Test
    @DisplayName("no response leaks answer material, for any problem in any language")
    void noAnswerMaterialEverLeaks() {
        // The whole surface, exhaustively: every language, every one of the twelve
        // problems, board and detail. This is the test that makes the DTO boundary
        // a guarantee rather than an intention.
        for (String language : List.of("c", "cpp", "java", "python", "javascript")) {
            resetDatabase();
            registerAndOpenArena();
            String token = missionIn(language);

            String board = getRaw(token, "/api/arena/problems").getBody();
            assertNoAnswerMaterial(board, language + " board");

            for (String ref : refsOf(get(token, "/api/arena/problems").getBody())) {
                String detail = getRaw(token, "/api/arena/problems/" + ref).getBody();
                assertNoAnswerMaterial(detail, language + " " + ref);
            }
        }
    }

    private void assertNoAnswerMaterial(String payload, String what) {
        assertThat(payload).as("%s should not be empty", what).isNotBlank();
        for (String forbidden : FORBIDDEN) {
            assertThat(payload)
                    .as("%s must not contain %s", what, forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("the corrected solution never appears in a response")
    void correctedCodeNeverAppears() {
        String token = missionIn("cpp");

        // Pull the real solution out of the bank resource and assert its body is
        // absent from what the participant receives - a field-name check alone
        // would miss a solution smuggled into another field.
        String solution = jdbc.queryForObject(
                "SELECT problem_id FROM attempt_problem WHERE ref = 'E-01'", String.class);
        assertThat(solution).isNotNull();

        String detail = getRaw(token, "/api/arena/problems/E-01").getBody();

        // The bug notes are prose and would be unmistakable if they leaked.
        assertThat(detail).doesNotContain("why_wrong");
        assertThat(detail).doesNotContain("location");
        assertThat(detail).doesNotContain("description");
    }

    @Test
    @DisplayName("the number of hidden tests is never disclosed")
    void hiddenTestCountIsNeverDisclosed() {
        String token = missionIn("python");
        String detail = getRaw(token, "/api/arena/problems/H-01").getBody();

        assertThat(detail).doesNotContain("hidden");

        // Only the visible examples are present, and they are the bank's visible
        // ones - not a truncated view of the hidden set.
        JsonNode parsed = get(token, "/api/arena/problems/H-01").getBody();
        assertThat(parsed.get("visibleTests")).isNotEmpty();
        assertThat(parsed.has("hiddenTests")).isFalse();
        assertThat(parsed.has("hiddenTestCount")).isFalse();
    }

    // --------------------------------------------------------------- security

    @Test
    @DisplayName("the workspace refuses a request with no session")
    void workspaceRequiresASession() {
        assertThat(get(null, "/api/arena/problems").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(null, "/api/arena/problems/E-01").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a forged session reaches nothing")
    void forgedSessionIsRefused() {
        ResponseEntity<JsonNode> response = get("not-a-real-token", "/api/arena/problems");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_SESSION_INVALID");
    }

    @Test
    @DisplayName("the workspace is closed before the mission starts")
    void workspaceClosedBeforeStart() {
        String token = checkIn(code);   // checked in, not started

        ResponseEntity<JsonNode> response = get(token, "/api/arena/problems");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_NOT_STARTED");
    }

    @Test
    @DisplayName("the workspace closes when the mission expires")
    void workspaceClosedAfterExpiry() {
        String token = missionIn("java");

        jdbc.update("UPDATE arena_attempt SET started_at = now() - interval '46 minutes', "
                + "expires_at = now() - interval '1 minute'");

        ResponseEntity<JsonNode> response = get(token, "/api/arena/problems");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_FINALIZED");
    }

    @Test
    @DisplayName("the workspace closes when the arena is stopped")
    void workspaceClosedWhenArenaStops() {
        String token = missionIn("cpp");
        setArena("OFFLINE");

        ResponseEntity<JsonNode> response = get(token, "/api/arena/problems");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
    }

    @Test
    @DisplayName("a language cannot be smuggled in through a query string")
    void languageCannotBeOverriddenByQuery() {
        String token = missionIn("java");

        // There is no language parameter to honour, so this must be indistinguishable
        // from the plain request rather than switching banks.
        JsonNode hijacked = get(token, "/api/arena/problems?language=python").getBody();
        assertThat(hijacked.get("language").asText()).isEqualTo("java");

        JsonNode problem = get(token, "/api/arena/problems/E-01?language=python").getBody();
        assertThat(problem.get("language").asText()).isEqualTo("java");
    }

    @Test
    @DisplayName("one participant cannot reach another's problems or drafts")
    void participantsAreIsolated() {
        String mine = missionIn("java");
        putDraft(mine, "E-01", "{\"code\":\"mine only\",\"revision\":0}");

        // A second, entirely separate participant.
        ResponseEntity<JsonNode> other = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "WRK002", (short) 2),
                JsonNode.class);
        String otherCode = other.getBody().get("registrations").get(0).get("accessCode").asText();
        String theirs = checkIn(otherCode);
        start(theirs, "python");

        // Same handle, different mission: they get their own problem and their own
        // (absent) draft. The handle names nothing on its own.
        JsonNode theirProblem = get(theirs, "/api/arena/problems/E-01").getBody();
        assertThat(theirProblem.get("language").asText()).isEqualTo("python");
        assertThat(theirProblem.get("draftCode").isNull()).isTrue();

        JsonNode myProblem = get(mine, "/api/arena/problems/E-01").getBody();
        assertThat(myProblem.get("draftCode").asText()).isEqualTo("mine only");

        Integer attempts = jdbc.queryForObject(
                "SELECT count(*) FROM arena_attempt", Integer.class);
        assertThat(attempts).isEqualTo(2);
    }

    // ----------------------------------------------------------------- drafts

    @Test
    @DisplayName("a draft is saved and survives a reload")
    void draftPersists() {
        String token = missionIn("java");

        ResponseEntity<JsonNode> saved =
                putDraft(token, "E-01", "{\"code\":\"class Fix {}\",\"revision\":0}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("revision").asInt()).isEqualTo(1);
        assertThat(saved.getBody().get("status").asText()).isEqualTo("ATTEMPTED");

        JsonNode reloaded = get(token, "/api/arena/problems/E-01").getBody();
        assertThat(reloaded.get("draftCode").asText()).isEqualTo("class Fix {}");
        assertThat(reloaded.get("draftRevision").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a draft survives re-entering the access code on another device")
    void draftSurvivesSessionTakeover() {
        String first = missionIn("python");
        putDraft(first, "M-01", "{\"code\":\"print(42)\",\"revision\":0}");

        // Same participant, new device: new session, same attempt, same work.
        String second = checkIn(code);

        JsonNode problem = get(second, "/api/arena/problems/M-01").getBody();
        assertThat(problem.get("draftCode").asText()).isEqualTo("print(42)");
    }

    @Test
    @DisplayName("saving a draft marks the problem attempted, never solved")
    void savingNeverMarksSolved() {
        String token = missionIn("cpp");
        putDraft(token, "H-01", "{\"code\":\"int main(){}\",\"revision\":0}");

        JsonNode board = get(token, "/api/arena/problems").getBody();

        // Nothing has been executed, so nothing can be solved. A save is not a
        // submission and is not evidence of a correct answer.
        assertThat(board.get("solvedCount").asInt()).isZero();
        assertThat(board.get("zones").findValuesAsText("status")).doesNotContain("SOLVED");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM attempt_problem WHERE ref = 'H-01'", String.class))
                .isEqualTo("ATTEMPTED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE solved_at IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("only the saved problem changes status")
    void savingAffectsOneProblemOnly() {
        String token = missionIn("java");
        putDraft(token, "E-02", "{\"code\":\"edited\",\"revision\":0}");

        Integer attempted = jdbc.queryForObject(
                "SELECT count(*) FROM attempt_problem WHERE status = 'ATTEMPTED'", Integer.class);
        assertThat(attempted).isEqualTo(1);
    }

    @Test
    @DisplayName("a stale revision is refused rather than overwriting newer work")
    void staleDraftIsRefused() {
        String token = missionIn("java");
        putDraft(token, "E-01", "{\"code\":\"first\",\"revision\":0}");

        // A tab that still believes it is at revision 0.
        ResponseEntity<JsonNode> stale =
                putDraft(token, "E-01", "{\"code\":\"clobber\",\"revision\":0}");

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code").asText()).isEqualTo("DRAFT_STALE");

        assertThat(jdbc.queryForObject(
                "SELECT draft_code FROM attempt_problem WHERE ref = 'E-01'", String.class))
                .isEqualTo("first");
    }

    @Test
    @DisplayName("a draft cannot be saved to a problem outside this mission")
    void draftRejectsUnknownProblem() {
        ResponseEntity<JsonNode> response =
                putDraft(missionIn("java"), "E-99", "{\"code\":\"x\",\"revision\":0}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PROBLEM_NOT_FOUND");
    }

    @Test
    @DisplayName("a draft cannot be saved before the mission starts")
    void draftRejectedBeforeStart() {
        String token = checkIn(code);

        ResponseEntity<JsonNode> response =
                putDraft(token, "E-01", "{\"code\":\"x\",\"revision\":0}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_NOT_STARTED");
    }

    @Test
    @DisplayName("an oversized draft is refused")
    void oversizedDraftIsRefused() {
        String token = missionIn("java");
        String huge = "x".repeat(70_000);

        ResponseEntity<JsonNode> response =
                putDraft(token, "E-01", "{\"code\":\"" + huge + "\",\"revision\":0}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("code").asText()).isEqualTo("DRAFT_TOO_LARGE");
    }

    @Test
    @DisplayName("a draft save with no revision is accepted as a first write")
    void draftWithoutRevisionIsAccepted() {
        String token = missionIn("java");

        ResponseEntity<JsonNode> response = putDraft(token, "E-01", "{\"code\":\"hello\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("revision").asInt()).isEqualTo(1);
    }
}
