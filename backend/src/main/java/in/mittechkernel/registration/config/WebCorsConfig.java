package in.mittechkernel.registration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The browser-facing CORS policy - one definition, used by the whole application.
 *
 * <p>This is exposed as a {@link CorsConfigurationSource} bean rather than as MVC
 * {@code addCorsMappings}, and {@code SecurityConfig} hands that same bean to Spring
 * Security. Two reasons:
 *
 * <ul>
 *   <li><b>One source of truth.</b> A policy declared in MVC and a second one declared
 *       in the security chain drift apart, and the symptom is a preflight that passes
 *       while the real request is blocked.
 *   <li><b>Error responses carry the headers too.</b> Security's {@code CorsFilter} runs
 *       before dispatch, so a 401, a 404 or a 500 still answers with
 *       {@code Access-Control-Allow-Origin}. An MVC-only policy attaches headers to
 *       matched handlers only, which is how a plain 404 on a mistyped path reaches the
 *       browser disguised as "CORS Missing Allow Origin" - the wrong bug to go hunting.
 * </ul>
 *
 * <h2>Origins are an explicit list</h2>
 *
 * <p>Never {@code "*"}. This API accepts registrations and carries an admin surface;
 * an allow-all policy is a habit that survives into production. The deployed frontend
 * origins and the Vite dev servers are named one by one, and the list is overridable
 * per environment with {@code CORS_ALLOWED_ORIGINS} so a new deployment needs a config
 * change, not a release.
 *
 * <h2>Credentials stay off</h2>
 *
 * <p>There is no cookie session and no browser-managed credential to send: the admin
 * panel builds its own {@code Authorization: Basic} header, which travels on the
 * strength of {@code allowedHeaders} and does not need
 * {@code Access-Control-Allow-Credentials}. Leaving it off keeps the policy the
 * narrower of the two.
 */
@Configuration
public class WebCorsConfig {

    private static final Logger log = LoggerFactory.getLogger(WebCorsConfig.class);

    /**
     * Everything the frontend actually issues. {@code OPTIONS} is listed for
     * completeness; preflight is answered by the filter before method matching.
     */
    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * {@code Content-Type} for JSON bodies, {@code Authorization} for admin Basic auth,
     * {@code Accept} because the clients send it on every request.
     */
    private static final List<String> ALLOWED_HEADERS =
            List.of("Content-Type", "Authorization", "Accept");

    private final List<String> allowedOrigins;

    public WebCorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (allowedOrigins.isEmpty()) {
            log.warn("app.cors.allowed-origins is empty. Every cross-origin browser "
                    + "request will be blocked. Set CORS_ALLOWED_ORIGINS.");
        } else {
            log.info("CORS allows origins: {}", allowedOrigins);
        }

        CorsConfiguration policy = new CorsConfiguration();
        policy.setAllowedOrigins(allowedOrigins);
        policy.setAllowedMethods(ALLOWED_METHODS);
        policy.setAllowedHeaders(ALLOWED_HEADERS);
        policy.setAllowCredentials(false);
        policy.setMaxAge(3600L);

        // "/**", not "/api/**": see the class comment - the health probe and every
        // error response should answer with the same headers.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", policy);
        return source;
    }
}
