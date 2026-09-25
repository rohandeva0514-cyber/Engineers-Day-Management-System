package in.mittechkernel.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Engineers' Day 2026 registration service.
 *
 * <p>Scope of this milestone: event discovery and server-authoritative registration.
 * No authentication, no event execution, no submissions, no results.
 */
@SpringBootApplication
public class RegistrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrationServiceApplication.class, args);
    }
}
