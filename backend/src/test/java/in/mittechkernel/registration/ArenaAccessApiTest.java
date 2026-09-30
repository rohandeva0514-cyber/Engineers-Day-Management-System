package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase B: check-in, session, attempt initialisation, language, Start Mission.
 *
 * <p>The assertions that matter most here are the ones about things that must NOT
 * happen: a second attempt, a raw token in the database, an old session still
 * working after a takeover, a language changing after the mission started, and a
 * client-supplied deadline. Each of those is a rule the frontend cannot be trusted
 * with, so each is tested against the API rather than against a component.
 */
class ArenaAccessApiTest extends PostgresIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    private String accessCode;

    /**
     * A real Debugging registration, taken through the public API so the access code
     * is issued exactly as it is on the day, and an arena that is open.
     */
    @BeforeEach
    void registerAndOpenArena() {
        ResponseEntity<JsonNode> registration = http.postForEntity(
                "/api/registrations", TestRequests.solo("debugging", "ARN001", (short) 1),
                JsonNode.class);

        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        accessCode = registration.getBody().get("registrations").get(0).get("accessCode").asText();
        assertThat(accessCode).matches("[2-9A-HJ-NP-Z]{8}");

        setArena("ACTIVE");
    }

    // ------------------------------------------------------------------ helpers

    private void setArena(String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD)
                .exchange("/api/admin/arena/status", HttpMethod.PATCH,
                        new HttpEntity<>("{\"status\":\"" + status + "\"}", headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> checkIn(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/arena/access", HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"" + code + "\"}", headers), JsonNode.class);
    }

    private String sessionToken() {
        ResponseEntity<JsonNode> response = checkIn(accessCode);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("sessionToken").asText();
    }

    /** A GET carrying the session bearer. The token never goes in a URL. */
    private ResponseEntity<JsonNode> attemptState(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange("/api/arena/attempt", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> put(String token, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.PUT, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String token, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> chooseLanguage(String token, String language) {
        return put(token, "/api/arena/attempt/language", "{\"language\":\"" + language + "\"}");
    }

    private ResponseEntity<JsonNode> startMission(String token, String language) {
        return post(token, "/api/arena/attempt/start", "{\"language\":\"" + language + "\"}");
    }

    private long attemptCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM arena_attempt", Integer.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------- access code

    @Test
    @DisplayName("a valid access code checks the participant in")
    void validCodeChecksIn() {
        ResponseEntity<JsonNode> response = checkIn(accessCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();

        assertThat(body.get("sessionToken").asText()).isNotBlank();
        assertThat(body.get("attempt").get("state").asText()).isEqualTo("INITIALIZED");

        // The clock has NOT started. This is the whole point of INITIALIZED: a
        // student who checks in and then has to move machines has lost nothing.
        assertThat(body.get("attempt").get("startedAt").isNull()).isTrue();
        assertThat(body.get("attempt").get("expiresAt").isNull()).isTrue();
        assertThat(body.get("attempt").get("remainingSeconds").asLong()).isZero();
    }

    @Test
    @DisplayName("a lower-case access code is accepted")
    void codeIsCaseInsensitive() {
        assertThat(checkIn(accessCode.toLowerCase()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = {"AAAAAAAA", "SHORT", "", "        ", "'; DROP TABLE registration; --"})
    @DisplayName("every bad code gets the same refusal, and none of them creates an attempt")
    void invalidCodesAreRefusedIdentically(String bad) {
        ResponseEntity<JsonNode> response = checkIn(bad);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ACCESS_CODE_INVALID");
        assertThat(attemptCount()).isZero();
    }

    @Test
    @DisplayName("a code for a different event does not open the Debugging arena")
    void codeFromAnotherEventIsRefused() {
        // Chess issues no access code at all, so its registration cannot produce one.
        // The guard that matters is the event check in ArenaAccessService: were a
        // second terminal-run event added, its codes must not work here.
        ResponseEntity<JsonNode> chess = http.postForEntity(
                "/api/registrations", TestRequests.solo("chess", "ARN900", (short) 1), JsonNode.class);

        assertThat(chess.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(chess.getBody().get("registrations").get(0).get("accessCode").isNull()).isTrue();
    }

    @Test
    @DisplayName("check-in reveals identity and nothing more")
    void identityIsMinimal() {
        JsonNode participant = checkIn(accessCode).getBody().get("participant");

        assertThat(participant.get("fullName").asText()).isEqualTo("Student ARN001");
        assertThat(participant.get("branch").asText()).isEqualTo("Computer Engineering");
        assertThat(participant.get("division").asText()).isEqualTo("A");
        assertThat(participant.get("yearLevel").asInt()).isEqualTo(1);
        assertThat(participant.get("eventName").asText()).isEqualTo("Debugging");

        // The session is held by whoever typed the code, which is usually but not
        // provably the person it belongs to. None of these are needed to recognise
        // yourself, so none of them are sent.
        assertThat(participant.has("email")).isFalse();
        assertThat(participant.has("phone")).isFalse();
        assertThat(participant.has("rollNo")).isFalse();
        assertThat(participant.has("participantId")).isFalse();
    }

    // ----------------------------------------------------------- arena control

    @Test
    @DisplayName("nobody checks in while the arena is offline")
    void offlineArenaRefusesCheckIn() {
        setArena("OFFLINE");

        ResponseEntity<JsonNode> response = checkIn(accessCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
        assertThat(attemptCount()).isZero();
    }

    @Test
    @DisplayName("an ended arena says so, rather than saying it has not started")
    void endedArenaRefusesCheckIn() {
        setArena("ENDED");

        ResponseEntity<JsonNode> response = checkIn(accessCode);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_ENDED");
    }

    @Test
    @DisplayName("a closed arena refuses before it looks at the code at all")
    void closedArenaDoesNotLeakCodeValidity() {
        setArena("OFFLINE");

        // A valid code and a nonsense one are indistinguishable while the arena is
        // shut, so the endpoint cannot be used to probe for codes out of hours.
        assertThat(checkIn(accessCode).getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
        assertThat(checkIn("AAAAAAAA").getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
    }

    @Test
    @DisplayName("pausing the arena does not destroy an attempt")
    void pausingPreservesTheAttempt() {
        String token = sessionToken();
        startMission(token, "cpp");

        setArena("OFFLINE");

        // The state endpoint still answers - a participant whose arena was paused has
        // to be told what happened, and refusing the one request that would tell them
        // would leave the page stuck.
        ResponseEntity<JsonNode> state = attemptState(token);
        assertThat(state.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(state.getBody().get("arenaStatus").asText()).isEqualTo("OFFLINE");
        assertThat(state.getBody().get("attempt").get("state").asText()).isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------------ session

    @Test
    @DisplayName("the raw session token is never stored")
    void rawTokenIsNotPersisted() {
        String token = sessionToken();

        String storedHash = jdbc.queryForObject(
                "SELECT session_token_hash FROM arena_attempt", String.class);

        assertThat(storedHash).isNotNull().hasSize(64).matches("[0-9a-f]{64}");
        assertThat(storedHash).isNotEqualTo(token);

        // And the token appears nowhere in the row at all.
        Integer matches = jdbc.queryForObject(
                "SELECT count(*) FROM arena_attempt WHERE session_token_hash = ?",
                Integer.class, token);
        assertThat(matches).isZero();
    }

    @Test
    @DisplayName("a protected endpoint refuses a request with no session")
    void protectedEndpointsRequireASession() {
        ResponseEntity<JsonNode> response =
                http.getForEntity("/api/arena/attempt", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_SESSION_INVALID");
    }

    @Test
    @DisplayName("a made-up bearer token is refused")
    void forgedTokenIsRefused() {
        ResponseEntity<JsonNode> response = attemptState("not-a-real-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_SESSION_INVALID");
    }

    @Test
    @DisplayName("a valid session reaches the attempt")
    void validSessionResolves() {
        ResponseEntity<JsonNode> state = attemptState(sessionToken());

        assertThat(state.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(state.getBody().get("attempt").get("state").asText()).isEqualTo("INITIALIZED");
        assertThat(state.getBody().get("participant").get("fullName").asText())
                .isEqualTo("Student ARN001");
    }

    // ---------------------------------------------------------- second device

    @Test
    @DisplayName("a second device resumes the same attempt rather than creating another")
    void secondDeviceResumesTheSameAttempt() {
        String firstToken = sessionToken();
        startMission(firstToken, "python");

        String secondToken = sessionToken();

        assertThat(attemptCount()).isEqualTo(1);
        assertThat(secondToken).isNotEqualTo(firstToken);

        JsonNode resumed = attemptState(secondToken).getBody().get("attempt");
        assertThat(resumed.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.get("language").asText()).isEqualTo("python");
    }

    @Test
    @DisplayName("the previous session stops working the moment a second device checks in")
    void takeoverInvalidatesThePreviousSession() {
        String firstToken = sessionToken();
        assertThat(attemptState(firstToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        String secondToken = sessionToken();

        // This is what stops two people sharing one code from working in parallel.
        assertThat(attemptState(firstToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(attemptState(secondToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("takeovers are counted, and a first check-in counts none")
    void takeoversAreCounted() {
        sessionToken();
        assertThat(takeovers()).isZero();

        sessionToken();
        assertThat(takeovers()).isEqualTo(1);

        sessionToken();
        assertThat(takeovers()).isEqualTo(2);
    }

    @Test
    @DisplayName("the takeover count is never shown to the participant")
    void takeoverCountIsNotExposed() {
        sessionToken();
        JsonNode body = checkIn(accessCode).getBody();

        assertThat(body.get("attempt").has("sessionTakeovers")).isFalse();
        assertThat(body.toString()).doesNotContain("takeover");
    }

    private int takeovers() {
        Integer count = jdbc.queryForObject(
                "SELECT session_takeovers FROM arena_attempt", Integer.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ attempt

    @Test
    @DisplayName("checking in repeatedly never creates a second attempt")
    void oneRegistrationGetsExactlyOneAttempt() {
        for (int i = 0; i < 5; i++) {
            assertThat(checkIn(accessCode).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the database refuses a second attempt for one registration")
    void secondAttemptIsImpossibleAtTheDatabase() {
        sessionToken();

        Long registrationId = jdbc.queryForObject(
                "SELECT registration_id FROM arena_attempt", Long.class);
        Long participantId = jdbc.queryForObject(
                "SELECT participant_id FROM arena_attempt", Long.class);

        // The application is not the thing stopping this. The constraint is.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO arena_attempt (registration_id, participant_id, state) "
                                + "VALUES (?, ?, 'INITIALIZED')",
                        registrationId, participantId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(attemptCount()).isEqualTo(1);
    }

    // ----------------------------------------------------------------- language

    @Test
    @DisplayName("all five languages are offered and each may be selected")
    void everyLanguageCanBeSelected() {
        String token = sessionToken();

        for (String language : new String[]{"c", "cpp", "java", "python", "javascript"}) {
            ResponseEntity<JsonNode> response = chooseLanguage(token, language);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("attempt").get("language").asText())
                    .isEqualTo(language);
        }
    }

    @Test
    @DisplayName("the language may be changed freely before the mission starts")
    void languageIsChangeableBeforeStart() {
        String token = sessionToken();

        chooseLanguage(token, "java");
        assertThat(attemptState(token).getBody().get("attempt").get("language").asText())
                .isEqualTo("java");

        chooseLanguage(token, "c");
        JsonNode attempt = attemptState(token).getBody().get("attempt");
        assertThat(attempt.get("language").asText()).isEqualTo("c");
        assertThat(attempt.get("languageLocked").asBoolean()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not a language")
    @ValueSource(strings = {"rust", "COBOL", "python3", "c++", "", "../../etc/passwd"})
    @DisplayName("an unsupported language is refused, never defaulted")
    void unsupportedLanguageIsRefused(String bad) {
        String token = sessionToken();
        ResponseEntity<JsonNode> response = chooseLanguage(token, bad);

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText())
                .isIn("LANGUAGE_NOT_SUPPORTED", "VALIDATION_FAILED");

        // Nothing was stored. A rejected language must not become the selected one.
        assertThat(jdbc.queryForObject("SELECT language FROM arena_attempt", String.class))
                .isNull();
    }

    @Test
    @DisplayName("the language is locked once the mission starts")
    void languageLocksAtStart() {
        String token = sessionToken();
        startMission(token, "cpp");

        ResponseEntity<JsonNode> response = chooseLanguage(token, "python");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_LANGUAGE_LOCKED");

        // And it really did not change - the lock is not just a message.
        assertThat(jdbc.queryForObject("SELECT language FROM arena_attempt", String.class))
                .isEqualTo("cpp");
    }

    @Test
    @DisplayName("the locked flag is reported to the client")
    void lockedFlagIsReported() {
        String token = sessionToken();
        assertThat(attemptState(token).getBody().get("attempt").get("languageLocked").asBoolean())
                .isFalse();

        startMission(token, "java");
        assertThat(attemptState(token).getBody().get("attempt").get("languageLocked").asBoolean())
                .isTrue();
    }

    // ------------------------------------------------------------ start mission

    @Test
    @DisplayName("Start Mission begins the clock and stamps a server-side deadline")
    void startMissionStampsTheDeadline() {
        Instant before = Instant.now();
        String token = sessionToken();

        ResponseEntity<JsonNode> response = startMission(token, "cpp");
        Instant after = Instant.now();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode attempt = response.getBody().get("attempt");

        assertThat(attempt.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(attempt.get("language").asText()).isEqualTo("cpp");

        Instant startedAt = Instant.parse(attempt.get("startedAt").asText());
        Instant expiresAt = Instant.parse(attempt.get("expiresAt").asText());

        assertThat(startedAt).isBetween(before.minusSeconds(5), after.plusSeconds(5));

        // 45 minutes, from the arena's configured duration and the server's clock.
        assertThat(Duration.between(startedAt, expiresAt)).isEqualTo(Duration.ofMinutes(45));

        long remaining = attempt.get("remainingSeconds").asLong();
        assertThat(remaining).isBetween(2640L, 2700L);
    }

    @Test
    @DisplayName("the deadline is the server's and the client cannot influence it")
    void clientCannotSetItsOwnDeadline() {
        String token = sessionToken();

        // Extra fields a hostile client might hope are read. They are not - the body
        // record has one component, and Jackson drops the rest.
        ResponseEntity<JsonNode> response = post(token, "/api/arena/attempt/start",
                "{\"language\":\"c\",\"expiresAt\":\"2099-01-01T00:00:00Z\","
                        + "\"durationSeconds\":99999,\"remainingSeconds\":99999}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Instant expiresAt = Instant.parse(
                response.getBody().get("attempt").get("expiresAt").asText());

        assertThat(expiresAt).isBefore(Instant.now().plus(Duration.ofMinutes(46)));
    }

    @Test
    @DisplayName("started_at and expires_at are persisted, not just returned")
    void startTimesArePersisted() {
        startMission(sessionToken(), "python");

        Instant startedAt = jdbc.queryForObject(
                "SELECT started_at FROM arena_attempt", Instant.class);
        Instant expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM arena_attempt", Instant.class);

        assertThat(startedAt).isNotNull();
        assertThat(expiresAt).isNotNull();
        assertThat(Duration.between(startedAt, expiresAt)).isEqualTo(Duration.ofMinutes(45));
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("starting twice is refused and does not restart the clock")
    void startingTwiceIsRefused() {
        String token = sessionToken();
        Instant firstExpiry = Instant.parse(
                startMission(token, "cpp").getBody().get("attempt").get("expiresAt").asText());

        ResponseEntity<JsonNode> again = startMission(token, "cpp");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_STARTED");

        Instant stored = jdbc.queryForObject("SELECT expires_at FROM arena_attempt", Instant.class);
        assertThat(stored).isEqualTo(firstExpiry);
    }

    @Test
    @DisplayName("Start Mission needs a language")
    void startRequiresALanguage() {
        String token = sessionToken();
        ResponseEntity<JsonNode> response = post(token, "/api/arena/attempt/start", "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("INITIALIZED");
    }

    @Test
    @DisplayName("Start Mission is refused while the arena is offline")
    void startRequiresAnActiveArena() {
        String token = sessionToken();
        setArena("OFFLINE");

        ResponseEntity<JsonNode> response = startMission(token, "cpp");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ARENA_OFFLINE");
        assertThat(jdbc.queryForObject("SELECT state FROM arena_attempt", String.class))
                .isEqualTo("INITIALIZED");
    }

    // ------------------------------------------------------------------ resume

    @Test
    @DisplayName("a refresh resumes the same mission with the clock still running")
    void refreshResumesTheMission() {
        String token = sessionToken();
        startMission(token, "java");

        // What a page reload does: same token from sessionStorage, one state call.
        JsonNode first = attemptState(token).getBody().get("attempt");
        JsonNode second = attemptState(token).getBody().get("attempt");

        assertThat(first.get("expiresAt").asText()).isEqualTo(second.get("expiresAt").asText());
        assertThat(second.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(second.get("language").asText()).isEqualTo("java");
        assertThat(attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("re-entering the access code resumes rather than restarting")
    void reEnteringTheCodeResumes() {
        String token = sessionToken();
        String expiry = startMission(token, "c").getBody().get("attempt").get("expiresAt").asText();

        JsonNode resumed = checkIn(accessCode).getBody().get("attempt");

        assertThat(resumed.get("state").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.get("expiresAt").asText()).isEqualTo(expiry);
        assertThat(resumed.get("language").asText()).isEqualTo("c");
    }

    @Test
    @DisplayName("a tentative language survives a refresh before the mission starts")
    void tentativeLanguageSurvivesRefresh() {
        String token = sessionToken();
        chooseLanguage(token, "javascript");

        JsonNode resumed = checkIn(accessCode).getBody().get("attempt");
        assertThat(resumed.get("state").asText()).isEqualTo("INITIALIZED");
        assertThat(resumed.get("language").asText()).isEqualTo("javascript");
        assertThat(resumed.get("languageLocked").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("an expired mission reports itself expired rather than counting below zero")
    void expiredAttemptReportsExpired() {
        String token = sessionToken();
        startMission(token, "cpp");

        // Move the whole mission window into the past rather than waiting 45 minutes.
        // Both ends, not just the deadline: ck_arena_attempt_window requires
        // expires_at > started_at, so an attempt that expired a minute ago is one
        // that started forty-six minutes ago. The state is derived from the clock, so
        // this is exactly what a real expiry looks like.
        jdbc.update("UPDATE arena_attempt SET started_at = now() - interval '46 minutes', "
                + "expires_at = now() - interval '1 minute'");

        JsonNode attempt = attemptState(token).getBody().get("attempt");

        assertThat(attempt.get("state").asText()).isEqualTo("EXPIRED");
        assertThat(attempt.get("remainingSeconds").asLong()).isZero();
    }

    @Test
    @DisplayName("a finished attempt cannot be started again")
    void finalizedAttemptCannotRestart() {
        String token = sessionToken();
        startMission(token, "cpp");

        jdbc.update("UPDATE arena_attempt SET state = 'SUBMITTED', finalized_at = now()");

        ResponseEntity<JsonNode> response = startMission(token, "cpp");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("ATTEMPT_ALREADY_FINALIZED");
    }

    @Test
    @DisplayName("a finished attempt is reported so the client can show the final screen")
    void finalizedAttemptIsReported() {
        String token = sessionToken();
        startMission(token, "cpp");
        jdbc.update("UPDATE arena_attempt SET state = 'SUBMITTED', finalized_at = now()");

        JsonNode attempt = attemptState(token).getBody().get("attempt");

        assertThat(attempt.get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(attempt.get("languageLocked").asBoolean()).isTrue();

        // Never a score. Reaching SUBMITTED says "recorded", not "you got 1700".
        assertThat(attempt.has("score")).isFalse();
        assertThat(attempt.has("solvedCount")).isFalse();
    }

    @Test
    @DisplayName("re-entering the code after finishing returns the final state, not a new attempt")
    void reEnteringAfterFinishingShowsFinalState() {
        startMission(sessionToken(), "cpp");
        jdbc.update("UPDATE arena_attempt SET state = 'SUBMITTED', finalized_at = now()");

        JsonNode attempt = checkIn(accessCode).getBody().get("attempt");

        assertThat(attempt.get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(attemptCount()).isEqualTo(1);
    }
}
