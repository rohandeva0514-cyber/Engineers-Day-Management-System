package in.mittechkernel.registration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CORS contract, from the browser's point of view.
 *
 * <p>These exist because the failure they guard against is invisible server-side: every
 * request in the log reads 200 while the browser discards the response for a missing
 * {@code Access-Control-Allow-Origin}. A method the frontend starts using, or a frontend
 * origin nobody added to the list, breaks production and no other test notices.
 *
 * <p>Origins under test come from {@code application-test.yml}.
 */
class CorsPolicyTest extends PostgresIntegrationTest {

    private static final String PROD_ORIGIN = "https://engineers-day-registration.vercel.app";
    private static final String DEV_ORIGIN = "http://localhost:5173";
    private static final String EVENTS_PATH = "/api/events";

    @ParameterizedTest(name = "{0} may read GET /api/events")
    @ValueSource(strings = {PROD_ORIGIN, DEV_ORIGIN})
    @DisplayName("an allowed origin gets Access-Control-Allow-Origin on the real request")
    void allowedOriginsCanReadTheCatalogue(String origin) {
        HttpHeaders request = new HttpHeaders();
        request.setOrigin(origin);

        ResponseEntity<String> response = http.exchange(
                "/api/events", HttpMethod.GET, new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(origin);
    }

    @ParameterizedTest(name = "preflight allows {0}")
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("preflight from the production origin allows every method the frontend uses")
    void preflightAllowsEveryMethodTheFrontendUses(String method) {
        ResponseEntity<Void> response = preflight(PROD_ORIGIN, method, EVENTS_PATH, "Content-Type");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(PROD_ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowMethods())
                .contains(HttpMethod.valueOf(method));
    }

    @Test
    @DisplayName("preflight allows the Authorization header the admin panel sends")
    void preflightAllowsAuthorizationHeader() {
        ResponseEntity<Void> response =
                preflight(PROD_ORIGIN, "PATCH", EVENTS_PATH, "Authorization", "Content-Type");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .contains("Authorization", "Content-Type");
    }

    @Test
    @DisplayName("preflight on a protected admin path succeeds without credentials")
    void preflightOnAdminPathIsNotAuthenticated() {
        // The browser sends no Authorization header on a preflight. If the security chain
        // answered 401 here, the admin panel could never issue the request that follows.
        ResponseEntity<Void> response = preflight(
                PROD_ORIGIN, "PATCH", "/api/admin/registration/status",
                "Authorization", "Content-Type");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo(PROD_ORIGIN);
    }

    @Test
    @DisplayName("an unlisted origin is refused - the policy is a list, not a wildcard")
    void unlistedOriginIsRefused() {
        ResponseEntity<Void> response = preflight("https://not-our-frontend.example", "GET", EVENTS_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("credentials are not enabled")
    void credentialsAreNotAllowed() {
        // Nothing depends on a cookie or a browser-managed credential; the header's
        // absence keeps the policy the narrower of the two.
        ResponseEntity<Void> response = preflight(PROD_ORIGIN, "GET", EVENTS_PATH);

        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isFalse();
    }

    private ResponseEntity<Void> preflight(String origin, String method, String path,
                                           String... requestHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.valueOf(method));
        if (requestHeaders.length > 0) {
            headers.setAccessControlRequestHeaders(java.util.List.of(requestHeaders));
        }
        return http.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);
    }
}
