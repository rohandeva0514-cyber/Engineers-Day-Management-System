package in.mittechkernel.registration.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness, and nothing else.
 *
 * <p>This answers one question: is a JVM serving HTTP on this port? It holds no
 * dependencies - no repository, no service, no {@code DataSource} - so it cannot
 * touch the database, the registration rules, or any participant data even by
 * accident. A request costs a route match and a serialised constant.
 *
 * <h2>Why this exists when /actuator/health already does</h2>
 *
 * <p>Actuator is installed and {@code /actuator/health} is exposed and public, and it
 * returns the same body. It is not the same check: its registry carries {@code db},
 * {@code diskSpace}, {@code ssl} and {@code ping} contributors, so every call opens a
 * pooled connection and runs a validation query against PostgreSQL. That is the right
 * behaviour for a readiness probe and the wrong behaviour for something polled every
 * five minutes forever, which is what this endpoint is for.
 *
 * <p>{@code /actuator/health} is untouched and still available for anything that
 * genuinely wants the deeper check.
 *
 * <h2>What this endpoint does NOT do</h2>
 *
 * <p>It does not keep anything awake by existing. A host that suspends an idle service
 * resumes it on inbound traffic, and the traffic has to come from somewhere outside
 * that host - see {@code KEEP_ALIVE.md}. Nothing inside this application can schedule
 * it, because a suspended process runs no schedulers.
 *
 * <p>It is public by the existing security rules: {@code SecurityConfig} authenticates
 * {@code /api/admin/**} and permits everything else. No security configuration was
 * changed to add this, and {@code HealthEndpointTest} pins both halves of that.
 *
 * <p>One caveat that belongs with the monitor, not with this class: public means "needs
 * no credentials", not "ignores credentials". Spring Security's Basic filter runs ahead
 * of authorization, so a request arriving with an Authorization header that does not
 * authenticate is refused with 401 before the permit rule is ever consulted. A monitor
 * configured with stray credentials would report this service down while it is healthy,
 * so it must send none.
 */
@RestController
public class HealthController {

    /** Allocated once. The response never varies, so neither should the object. */
    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping("/health")
    public Map<String, String> health() {
        return UP;
    }
}
