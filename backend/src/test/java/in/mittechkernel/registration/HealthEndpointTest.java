package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keep-alive target.
 *
 * <p>An external monitor calls this every five minutes, unauthenticated, forever. Two
 * things therefore have to stay true, and a change to either is a production outage of
 * a kind nobody notices until the service is asleep: it must answer 200 without
 * credentials, and adding it must not have opened a hole anywhere else.
 *
 * <p>{@code TestRestTemplate} sends no credentials unless asked, so every request here
 * is anonymous - which is exactly the monitor's position.
 */
class HealthEndpointTest extends PostgresIntegrationTest {

    @Test
    @DisplayName("GET /health returns 200 with status UP, unauthenticated")
    void healthIsUpAndPublic() {
        ResponseEntity<JsonNode> response = http.getForEntity("/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("the body is exactly {\"status\":\"UP\"} as JSON")
    void bodyShapeIsStable() {
        // A monitor may be configured to match on the body, so the shape is a contract
        // and not an implementation detail.
        ResponseEntity<String> response = http.getForEntity("/health", String.class);

        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("BAD credentials are refused even here - configure the monitor with none")
    void badCredentialsAreRefusedEvenOnAPermittedPath() {
        // Worth pinning, because it surprises people and the failure mode is a monitor
        // that reports the service down while it is perfectly healthy.
        //
        // Spring Security's Basic filter runs BEFORE authorization. An Authorization
        // header that does not authenticate fails the request there and never reaches
        // the permitAll rule that would otherwise have allowed it. "Public" here means
        // "needs no credentials", not "ignores whatever credentials you send".
        //
        // The monitor must therefore send NO Authorization header at all - which is the
        // default everywhere - and KEEP_ALIVE.md says so.
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("nobody", "wrong-password");

        ResponseEntity<JsonNode> response = http.exchange(
                "/health", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the admin API is still protected - /health opened nothing else")
    void adminRemainsProtected() {
        assertThat(http.getForEntity("/api/admin/dashboard", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/admin/events", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/api/admin/participants", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("wrong admin credentials are still refused")
    void adminRejectsBadCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin", "not-the-password");

        ResponseEntity<String> response = http.exchange(
                "/api/admin/dashboard", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the public catalogue is unaffected")
    void publicApiStillWorks() {
        assertThat(http.getForEntity("/api/events", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
