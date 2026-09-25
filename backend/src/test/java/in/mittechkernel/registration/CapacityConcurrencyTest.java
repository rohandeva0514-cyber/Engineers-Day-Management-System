package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capacity under contention - the test this milestone exists for.
 *
 * <p>BuildX has 30 seats and will be claimed by a burst of students hitting submit within the
 * same few seconds. The naive implementation - count the rows, compare to 30, then insert -
 * passes every sequential test and oversells immediately in production, because fifty
 * requests all read 29 before any of them writes.
 *
 * <p>So this fires fifty genuinely simultaneous registrations, released together from a
 * latch, and asserts that exactly thirty win. A sequential test cannot distinguish a correct
 * implementation from a broken one here; only a contended one can.
 */
class CapacityConcurrencyTest extends PostgresIntegrationTest {

    private static final int BUILDX_SEATS = 30;
    private static final int CONCURRENT_ATTEMPTS = 50;

    @Test
    @DisplayName("BuildX never exceeds 30 registrations under 50 simultaneous attempts")
    void buildXCannotBeOversold() throws Exception {
        CountDownLatch startGun = new CountDownLatch(1);
        List<Callable<ResponseEntity<JsonNode>>> attempts = IntStream.rangeClosed(1, CONCURRENT_ATTEMPTS)
                .mapToObj(i -> (Callable<ResponseEntity<JsonNode>>) () -> {
                    startGun.await();  // everyone blocks here, then goes at once
                    return http.postForEntity("/api/registrations",
                            TestRequests.solo("buildx", String.format("1MS24BX%03d", i), (short) 1),
                            JsonNode.class);
                })
                .toList();

        List<ResponseEntity<JsonNode>> responses;
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS)) {
            List<Future<ResponseEntity<JsonNode>>> futures = attempts.stream()
                    .map(pool::submit)
                    .toList();
            startGun.countDown();

            responses = futures.stream().map(future -> {
                try {
                    return future.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("a registration attempt did not complete", e);
                }
            }).toList();
        }

        Map<HttpStatus, Long> byStatus = responses.stream()
                .collect(Collectors.groupingBy(
                        response -> HttpStatus.valueOf(response.getStatusCode().value()),
                        Collectors.counting()));

        // Exactly 30 succeed. Not "at most" - if fewer won, the conditional UPDATE is
        // rejecting claims it should have accepted, which is just as wrong.
        assertThat(byStatus.get(HttpStatus.CREATED))
                .as("successful registrations out of %d attempts", CONCURRENT_ATTEMPTS)
                .isEqualTo(BUILDX_SEATS);

        assertThat(byStatus.get(HttpStatus.CONFLICT))
                .as("refused registrations")
                .isEqualTo(CONCURRENT_ATTEMPTS - BUILDX_SEATS);

        // Every refusal is the honest reason, not a 500 from a constraint nobody translated.
        responses.stream()
                .filter(response -> response.getStatusCode().value() == 409)
                .forEach(response -> assertThat(response.getBody().get("code").asText())
                        .isEqualTo("CAPACITY_FULL"));

        // The seat counter and the registration rows agree, and neither exceeds the cap.
        assertThat(countRegistrations("buildx")).isEqualTo(BUILDX_SEATS);
        assertThat(seatsTaken("buildx")).isEqualTo(BUILDX_SEATS);
    }

    @Test
    @DisplayName("a sold-out BuildX reports SOLD_OUT rather than staying OPEN with no seats")
    void soldOutStatusIsDerivedFromSeats() {
        setCapacity("buildx", 2);

        assertThat(register("buildx", "1MS24BX901").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register("buildx", "1MS24BX902").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> event = http.getForEntity("/api/events/buildx", JsonNode.class);
        assertThat(event.getBody().get("registrationStatus").asText()).isEqualTo("SOLD_OUT");
        assertThat(event.getBody().get("registrationOpen").asBoolean()).isFalse();
        assertThat(event.getBody().get("seatsRemaining").asInt()).isZero();

        ResponseEntity<JsonNode> refused = register("buildx", "1MS24BX903");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code").asText()).isEqualTo("CAPACITY_FULL");
    }

    @Test
    @DisplayName("Ideathon capacity is configurable and counts teams, not people")
    void ideathonCapacityIsConfigurableAndCountsTeams() {
        setCapacity("ideathon", 2);

        // A four-person team consumes one seat, because Ideathon's capacity unit is TEAM.
        ResponseEntity<JsonNode> first =
                http.postForEntity("/api/registrations",
                        TestRequests.team("ideathon", "Alpha", "IA", 4, (short) 1), JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("seatsRemaining").asInt()).isEqualTo(1);
        assertThat(countRegistrations("ideathon")).isEqualTo(4);
        assertThat(seatsTaken("ideathon")).isEqualTo(1);

        assertThat(http.postForEntity("/api/registrations",
                TestRequests.team("ideathon", "Beta", "IB", 2, (short) 1), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> third = http.postForEntity("/api/registrations",
                TestRequests.team("ideathon", "Gamma", "IC", 1, (short) 1), JsonNode.class);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody().get("code").asText()).isEqualTo("CAPACITY_FULL");

        assertThat(seatsTaken("ideathon")).isEqualTo(2);
    }

    @Test
    @DisplayName("an event with no capacity limit accepts registrations without a seat counter")
    void manualClosureEventsHaveNoSeatLimit() {
        for (int i = 1; i <= 25; i++) {
            assertThat(register("chess", String.format("1MS24CH%03d", i)).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<JsonNode> event = http.getForEntity("/api/events/chess", JsonNode.class);
        assertThat(event.getBody().get("capacity").isNull()).isTrue();
        assertThat(event.getBody().get("seatsRemaining").isNull()).isTrue();
        assertThat(event.getBody().get("registrationStatus").asText()).isEqualTo("REGISTRATION_OPEN");
    }

    private ResponseEntity<JsonNode> register(String eventId, String rollNo) {
        return http.postForEntity("/api/registrations",
                TestRequests.solo(eventId, rollNo, (short) 1), JsonNode.class);
    }
}
