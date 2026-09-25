package in.mittechkernel.registration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Who may call what.
 *
 * <p>Two rules, and only two:
 *
 * <pre>
 *   /api/admin/**   requires an authenticated ADMIN
 *   everything else stays public
 * </pre>
 *
 * <p>The public API is unchanged - students register without accounts, and this
 * must not become a login wall in front of registration on event day.
 *
 * <h2>Why HTTP Basic</h2>
 *
 * <p>There is one operator account, used from a browser on an internal panel. Basic
 * over TLS needs no session table, no token rotation, no refresh flow and no
 * additional dependency, and Spring's own filter implements it. A JWT scheme here
 * would be more moving parts protecting the same single credential.
 *
 * <h2>Credentials come from the environment, and there is no default</h2>
 *
 * <p>If {@code ADMIN_PASSWORD} is unset, NO admin user is registered and every
 * {@code /api/admin/**} request is refused. That is deliberate: a fallback password
 * is the thing that ships to production by accident. A service with no admin
 * account is inconvenient; one with a guessable admin account is a breach.
 *
 * <p>The password is hashed with BCrypt at startup and the plaintext is never
 * stored, logged, or compared directly.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String adminUsername;
    private final String adminPassword;

    public SecurityConfig(@Value("${app.admin.username:admin}") String adminUsername,
                          @Value("${app.admin.password:}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService adminUsers(PasswordEncoder encoder) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set. No admin account exists and every "
                    + "/api/admin/** request will be refused with 401. Set it to enable "
                    + "the admin panel.");
            return new InMemoryUserDetailsManager();
        }

        log.info("Admin account '{}' is enabled.", adminUsername);
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername)
                        .password(encoder.encode(adminPassword))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        return http
                // The one policy from WebCorsConfig, injected rather than discovered,
                // so this chain cannot silently lose its CORS handling if the bean is
                // ever renamed. Security's CorsFilter runs ahead of the rest of the
                // chain, so preflight is answered and every response - including a 401
                // from this chain - carries the Access-Control-* headers.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // No browser form posts and no cookie session, so there is no CSRF
                // vector to protect: every request carries its own credentials.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Preflight carries no credentials by design.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything the students use, plus the health probe.
                        .anyRequest().permitAll())

                .httpBasic(Customizer.withDefaults())

                // 401 with no WWW-Authenticate challenge: a browser pop-up would
                // hijack the panel's own sign-in form and cannot be dismissed.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
