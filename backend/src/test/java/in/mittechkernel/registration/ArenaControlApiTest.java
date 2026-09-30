package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arena lifecycle switch.
 *
 * <p>This is the control that decides whether a competition is running, so the
 * things worth asserting are the refusals rather than the happy path: that the
 * arena starts OFFLINE, that an unauthenticated caller cannot move it, that a
 * finished arena cannot be restarted in one step, and that the public endpoint
 * reports the switch without reporting anything about who flipped it.
 */
class ArenaControlApiTest extends PostgresIntegrationTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    private TestRestTemplate asAdmin() {
        return http.withBasicAuth(ADMIN_USER, ADMIN_PASSWORD);
    }

    private ResponseEntity<JsonNode> setStatus(TestRestTemplate client, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.exchange("/api/admin/arena/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"" + status + "\"}", headers), JsonNode.class);
    }

    private String publicStatus() {
        return http.getForEntity("/api/arena/status", JsonNode.class)
                .getBody().get("status").asText();
    }

    // ------------------------------------------------------------------ public

    @Test
    @DisplayName("the arena is OFFLINE until an organiser starts it")
    void arenaStartsOffline() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/arena/status", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();

        // The whole point of Phase A: a deployed frontend does not make the arena
        // reachable. Only this row does.
        assertThat(body.get("status").asText()).isEqualTo("OFFLINE");
        assertThat(body.get("eventId").asText()).isEqualTo("debugging");
    }

    @Test
    @DisplayName("the status endpoint carries the mission length and the server clock")
    void statusCarriesDurationAndServerTime() {
        JsonNode body = http.getForEntity("/api/arena/status", JsonNode.class).getBody();

        // 45 minutes. Every countdown in the arena is anchored to this and to
        // serverTime, never to the browser's clock.
        assertThat(body.get("durationSeconds").asInt()).isEqualTo(2700);
        assertThat(body.get("serverTime").asText()).isNotBlank();
    }

    @Test
    @DisplayName("all five debugging languages are offered")
    void statusListsEveryLanguage() {
        JsonNode body = http.getForEntity("/api/arena/status", JsonNode.class).getBody();

        List<String> ids = body.get("languages").findValuesAsText("id");
        assertThat(ids).containsExactly("c", "cpp", "java", "python", "javascript");

        body.get("languages").forEach(language -> {
            assertThat(language.get("label").asText()).isNotBlank();
            assertThat(language.get("runtime").asText()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the public endpoint reveals nothing about who operates the arena")
    void publicStatusLeaksNoOperationalDetail() {
        setStatus(asAdmin(), "ACTIVE");

        JsonNode body = http.getForEntity("/api/arena/status", JsonNode.class).getBody();

        // Polled every ten seconds by anyone on the internet. It says whether a door
        // is open, and nothing whatever about who opened it or when.
        assertThat(body.has("updatedBy")).isFalse();
        assertThat(body.has("openedAt")).isFalse();
        assertThat(body.has("updatedAt")).isFalse();
    }

    // ------------------------------------------------------------------- admin

    @Test
    @DisplayName("an unauthenticated caller cannot start the arena")
    void arenaControlRequiresAuthentication() {
        assertThat(setStatus(http, "ACTIVE").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/admin/arena", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // And it really did not take effect.
        assertThat(publicStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("wrong admin credentials cannot start the arena")
    void arenaControlRejectsBadCredentials() {
        assertThat(setStatus(http.withBasicAuth(ADMIN_USER, "wrong"), "ACTIVE").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(publicStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("an organiser starts the arena and every participant sees it")
    void adminStartsTheArena() {
        ResponseEntity<JsonNode> response = setStatus(asAdmin(), "ACTIVE");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("ACTIVE");

        // Stamped on entry to the state, and attributed to the authenticated
        // principal rather than to anything the client sent.
        assertThat(response.getBody().get("openedAt").asText()).isNotBlank();
        assertThat(response.getBody().get("updatedBy").asText()).isEqualTo(ADMIN_USER);

        assertThat(publicStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("an organiser stops the arena")
    void adminStopsTheArena() {
        setStatus(asAdmin(), "ACTIVE");
        assertThat(setStatus(asAdmin(), "OFFLINE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("starting an already-started arena is not an error")
    void repeatedStartIsIdempotent() {
        setStatus(asAdmin(), "ACTIVE");

        // A double-click, or a retried request on a flaky hall network. Neither is a
        // mistake the organiser should have to think about.
        ResponseEntity<JsonNode> again = setStatus(asAdmin(), "ACTIVE");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("status").asText()).isEqualTo("ACTIVE");
    }

    // -------------------------------------------------------- state machine

    @Test
    @DisplayName("an ended arena cannot be restarted in one step")
    void endedArenaCannotJumpBackToActive() {
        setStatus(asAdmin(), "ACTIVE");
        setStatus(asAdmin(), "ENDED");

        ResponseEntity<JsonNode> refused = setStatus(asAdmin(), "ACTIVE");

        // The refusal that matters. ENDED means every remaining attempt has been
        // finalized; putting the competition back in front of those students must
        // not be reachable by one mis-click.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code").asText()).isEqualTo("ARENA_TRANSITION_INVALID");
        assertThat(refused.getBody().get("message").asText()).contains("OFFLINE first");

        assertThat(publicStatus()).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("an ended arena reopens deliberately, via OFFLINE")
    void endedArenaReopensThroughOffline() {
        setStatus(asAdmin(), "ACTIVE");
        setStatus(asAdmin(), "ENDED");

        assertThat(setStatus(asAdmin(), "OFFLINE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setStatus(asAdmin(), "ACTIVE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a paused arena can still be ended")
    void offlineArenaCanBeEnded() {
        setStatus(asAdmin(), "ACTIVE");
        setStatus(asAdmin(), "OFFLINE");

        // Paused for a problem, then the organiser decides the event is over.
        assertThat(setStatus(asAdmin(), "ENDED").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicStatus()).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("ending the arena records when it ended")
    void endingStampsEndedAt() {
        setStatus(asAdmin(), "ACTIVE");
        ResponseEntity<JsonNode> ended = setStatus(asAdmin(), "ENDED");

        assertThat(ended.getBody().get("endedAt").asText()).isNotBlank();
        assertThat(ended.getBody().get("openedAt").asText()).isNotBlank();
    }

    // --------------------------------------------------------------- validation

    @Test
    @DisplayName("an unknown status is refused in the standard error shape")
    void unknownStatusIsRefused() {
        ResponseEntity<JsonNode> response = setStatus(asAdmin(), "PAUSED");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().get("details").get("allowed").asText())
                .isEqualTo("OFFLINE, ACTIVE, ENDED");

        assertThat(publicStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("the admin dashboard reports the switch and who last moved it")
    void adminDashboardReportsControl() {
        setStatus(asAdmin(), "ACTIVE");

        ResponseEntity<JsonNode> response =
                asAdmin().getForEntity("/api/admin/arena", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode control = response.getBody().get("control");
        assertThat(control.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(control.get("durationSeconds").asInt()).isEqualTo(2700);
        assertThat(control.get("updatedBy").asText()).isEqualTo(ADMIN_USER);
    }
}
