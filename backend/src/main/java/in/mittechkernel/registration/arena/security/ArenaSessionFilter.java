package in.mittechkernel.registration.arena.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.service.ArenaSessionService;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a bearer token into an authenticated arena participant.
 *
 * <p>Sits ahead of Spring Security's Basic filter and acts only on {@code
 * /api/arena/**}. The admin panel's credentials and this one's tokens never meet:
 * a Basic header on an arena path is ignored here, and a Bearer header on an admin
 * path is not looked at.
 *
 * <h2>Why it writes the 401 itself</h2>
 *
 * <p>The chain's entry point returns a bare 401 with no body, which is right for the
 * admin panel - a browser pop-up would hijack its sign-in form. But the arena client
 * has to tell "your session lapsed, type your code again" apart from any other
 * failure, and it branches on {@code code} rather than on a status. So an arena path
 * with a missing or dead token is refused here, in the one error shape every other
 * endpoint uses.
 *
 * <h2>Why every failure looks the same</h2>
 *
 * <p>Absent, malformed, unknown, expired, and displaced-by-another-device all produce
 * ARENA_SESSION_INVALID. Telling them apart would let someone holding a stolen token
 * learn whether it had ever been real.
 */
@Component
public class ArenaSessionFilter extends OncePerRequestFilter {

    private static final String ARENA_PREFIX = "/api/arena/";

    /**
     * The two arena endpoints that must work without a session.
     *
     * <p>{@code /status} is the gate a waiting participant polls, and {@code /access}
     * is how a session is obtained in the first place. Everything else under the
     * prefix requires one - including any endpoint added later, which is the point of
     * matching on the prefix rather than listing protected paths.
     */
    private static final List<String> PUBLIC_PATHS =
            List.of("/api/arena/status", "/api/arena/access");

    static final String ROLE = "ROLE_ARENA_PARTICIPANT";

    private final ArenaSessionService sessions;
    private final ObjectMapper objectMapper;

    public ArenaSessionFilter(ArenaSessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Preflight carries no credentials by design, and CORS answers it earlier in
        // the chain. Filtering it here would refuse the request that precedes every
        // real one.
        return !path.startsWith(ARENA_PREFIX)
                || PUBLIC_PATHS.contains(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Optional<ArenaAttempt> attempt = bearerToken(request)
                .flatMap(token -> sessions.resolve(token, Instant.now()));

        if (attempt.isEmpty()) {
            refuse(request, response);
            return;
        }

        ArenaPrincipal principal = ArenaPrincipal.of(attempt.get());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority(ROLE)));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            // Stateless: nothing may leak into the next request on this thread.
            SecurityContextHolder.clearContext();
        }
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String value = header.substring(7).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorCode.ARENA_SESSION_INVALID,
                "Your arena session is no longer valid. Enter your access code again.",
                request.getRequestURI(),
                Map.of());

        response.setStatus(ApiErrorCode.ARENA_SESSION_INVALID.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
