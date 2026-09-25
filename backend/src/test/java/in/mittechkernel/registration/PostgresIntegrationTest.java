package in.mittechkernel.registration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test that touches the database.
 *
 * <p>Tests run against a real PostgreSQL, not H2. The capacity guarantee depends on
 * PostgreSQL's behaviour for a conditional UPDATE under concurrent transactions, and the
 * schema depends on partial unique indexes and expression indexes. An in-memory substitute
 * would pass these tests while the thing they are protecting stayed broken.
 *
 * <p>One container is shared by the whole suite - started once, reused, and left to
 * Testcontainers' Ryuk to clean up - because starting a database per test class would
 * dominate the runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("engineers_day_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Return the database to its freshly-migrated state before each test.
     *
     * <p>Registrations are cleared and every event is restored to the values seeded by
     * {@code V2__seed_events.sql}, explicitly rather than by re-running the migration.
     * Tests that deliberately close an event or shrink a capacity therefore cannot leak into
     * the next one - which is the failure mode that makes a concurrency suite flaky.
     */
    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE registration, team_member, team, participant RESTART IDENTITY CASCADE");
        jdbc.execute("UPDATE event SET seats_taken = 0, registration_status = 'REGISTRATION_OPEN'");
        jdbc.execute("UPDATE event SET capacity = 30 WHERE id = 'buildx'");
        jdbc.execute("UPDATE event SET capacity = 40 WHERE id = 'ideathon'");
        jdbc.execute("UPDATE event SET capacity = NULL WHERE id NOT IN ('buildx', 'ideathon')");
    }

    protected void closeRegistration(String eventId) {
        jdbc.update("UPDATE event SET registration_status = 'REGISTRATION_CLOSED' WHERE id = ?",
                eventId);
    }

    protected void setCapacity(String eventId, Integer capacity) {
        jdbc.update("UPDATE event SET capacity = ? WHERE id = ?", capacity, eventId);
    }

    protected int countRegistrations(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM registration WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    protected int seatsTaken(String eventId) {
        Integer seats = jdbc.queryForObject(
                "SELECT seats_taken FROM event WHERE id = ?", Integer.class, eventId);
        return seats == null ? 0 : seats;
    }
}
