package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import in.mittechkernel.registration.dto.ParticipantRequest;
import in.mittechkernel.registration.dto.RegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registration rules, exercised end to end through HTTP.
 *
 * <p>These go through the real controller, the real validator, the real transaction and the
 * real database rather than mocking the service: the rules being protected here are as much
 * about status codes, error codes and constraints as they are about Java logic, and a mocked
 * test would assert the parts that cannot go wrong.
 */
class RegistrationRulesApiTest extends PostgresIntegrationTest {

    // ------------------------------------------------------------ happy paths

    @Test
    @DisplayName("an eligible first year can register for a solo event")
    void eligibleParticipantCanRegisterSolo() {
        ResponseEntity<JsonNode> response = register(
                TestRequests.solo("buildx", "1MS24CS001", (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("eventId").asText()).isEqualTo("buildx");
        assertThat(body.get("team").isNull()).isTrue();
        assertThat(body.get("registrations")).hasSize(1);
        assertThat(body.get("seatsRemaining").asInt()).isEqualTo(29);

        // The database generates created_at, so Hibernate has to read it back after the
        // INSERT. Without that, a successful POST returns registeredAt: null while the same
        // registration fetched a second later shows the real time.
        assertThat(body.get("registeredAt").isNull())
                .as("registeredAt must be populated on the response to a successful POST")
                .isFalse();

        assertThat(countRegistrations("buildx")).isEqualTo(1);
        assertThat(seatsTaken("buildx")).isEqualTo(1);
    }

    @Test
    @DisplayName("Tech Debate accepts a team of exactly 10")
    void debateAcceptsExactlyTen() {
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("tech-debate", "Kernel Panic", "DB", 10, (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("registrations")).hasSize(10);
        assertThat(body.get("team").get("size").asInt()).isEqualTo(10);
        assertThat(body.get("team").get("captainRollNo").asText()).isEqualTo("DB001");

        // Ten people, ten registration rows, one team.
        assertThat(countRegistrations("tech-debate")).isEqualTo(10);
    }

    @Test
    @DisplayName("a participant can register for several eligible events")
    void multiEventRegistrationIsAllowed() {
        ParticipantRequest student = TestRequests.participant("1MS24CS050", (short) 1);

        assertThat(register(new RegistrationRequest("chess", null, List.of(student), null))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register(new RegistrationRequest("buildx", null, List.of(student), null))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register(new RegistrationRequest("debugging", null, List.of(student), null))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> mine =
                http.getForEntity("/api/registrations?rollNo=1MS24CS050", JsonNode.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).isNotNull();
        assertThat(mine.getBody().get("registrations")).hasSize(3);
    }

    // ------------------------------------------------------------ eligibility

    @ParameterizedTest(name = "{0} rejects a year {1} student")
    @CsvSource({
            "buildx, 2",          // first year only
            "debugging, 2",       // first year only
            "rapid-research, 1"   // second year only
    })
    @DisplayName("an ineligible year is refused")
    void ineligibleYearIsRefused(String eventId, short year) {
        RegistrationRequest request = "rapid-research".equals(eventId)
                ? TestRequests.team(eventId, "Wrong Year", "WY", 2, year)
                : TestRequests.solo(eventId, "WY" + eventId.toUpperCase().charAt(0) + year, year);

        ResponseEntity<JsonNode> response = register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("INELIGIBLE_YEAR");
        assertThat(countRegistrations(eventId)).isZero();
    }

    @Test
    @DisplayName("eligibility uses the stored year, not the year the client claims")
    void clientCannotLieAboutYear() {
        // Registered honestly as a second year for Chess.
        assertThat(register(TestRequests.solo("chess", "1MS23IS009", (short) 2)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Now claims to be a first year to reach BuildX. The stored record wins.
        ResponseEntity<JsonNode> response = register(
                new RegistrationRequest("buildx", null,
                        List.of(new ParticipantRequest("1MS23IS009", "Student 1MS23IS009",
                                "1ms23is009@mit.example.edu", (short) 1)), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("PARTICIPANT_IDENTITY_CONFLICT");
        assertThat(countRegistrations("buildx")).isZero();
    }

    // ------------------------------------------------------------ roster shape

    @Test
    @DisplayName("a solo event rejects a team registration")
    void soloEventRejectsTeam() {
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("buildx", "Not Allowed", "SL", 3, (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("SOLO_EVENT_REJECTS_TEAM");
        assertThat(countRegistrations("buildx")).isZero();
    }

    @Test
    @DisplayName("a solo event rejects a team name even with one participant")
    void soloEventRejectsTeamName() {
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("chess", "Solo Squad", "SN", 1, (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("SOLO_EVENT_REJECTS_TEAM");
    }

    @ParameterizedTest(name = "Ideathon rejects a team of {0}")
    @ValueSource(ints = {5, 7, 12})
    @DisplayName("a team event rejects a roster outside its size range")
    void teamEventRejectsInvalidSize(int size) {
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("ideathon", "Too Big " + size, "TB" + size, size, (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("INVALID_TEAM_SIZE");
        assertThat(detailsOf(response).get("maxTeamSize").asInt()).isEqualTo(4);
        assertThat(countRegistrations("ideathon")).isZero();
    }

    @ParameterizedTest(name = "Tech Debate rejects a team of {0}")
    @ValueSource(ints = {1, 9, 11})
    @DisplayName("Tech Debate rejects anything other than exactly 10")
    void debateRejectsWrongSize(int size) {
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("tech-debate", "Wrong Size " + size, "WS" + size, size, (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("INVALID_TEAM_SIZE");
        assertThat(response.getBody().get("message").asText()).contains("exactly 10");
        assertThat(countRegistrations("tech-debate")).isZero();
    }

    @Test
    @DisplayName("a team event requires a team name")
    void teamEventRequiresName() {
        ResponseEntity<JsonNode> response = register(
                new RegistrationRequest("fix-it", null, TestRequests.roster("FN", 3, (short) 1), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("TEAM_NAME_REQUIRED");
    }

    @Test
    @DisplayName("the same person cannot appear twice on one roster")
    void rosterRejectsDuplicateMember() {
        ParticipantRequest twice = TestRequests.participant("1MS24CS077", (short) 1);
        ResponseEntity<JsonNode> response = register(
                TestRequests.team("ideathon", "Doubled", List.of(twice, twice)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(response)).isEqualTo("DUPLICATE_PARTICIPANT_IN_ROSTER");
    }

    // ------------------------------------------------------------ state + duplicates

    @Test
    @DisplayName("a closed event rejects registration")
    void closedEventRejectsRegistration() {
        closeRegistration("chess");

        ResponseEntity<JsonNode> response = register(
                TestRequests.solo("chess", "1MS24CS100", (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("REGISTRATION_CLOSED");
        assertThat(countRegistrations("chess")).isZero();
    }

    @Test
    @DisplayName("registering twice for the same event is rejected")
    void duplicateRegistrationIsRejected() {
        assertThat(register(TestRequests.solo("debugging", "1MS24CS200", (short) 1))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> second =
                register(TestRequests.solo("debugging", "1MS24CS200", (short) 1));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(second)).isEqualTo("DUPLICATE_REGISTRATION");
        assertThat(countRegistrations("debugging")).isEqualTo(1);
    }

    @Test
    @DisplayName("a member of one team cannot join a second team in the same event")
    void participantCannotJoinTwoTeamsInOneEvent() {
        List<ParticipantRequest> first = TestRequests.roster("TT", 3, (short) 1);
        assertThat(register(TestRequests.team("fix-it", "First Team", first)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        List<ParticipantRequest> second = new ArrayList<>(TestRequests.roster("UU", 2, (short) 1));
        second.add(first.get(0));  // already on First Team

        ResponseEntity<JsonNode> response =
                register(TestRequests.team("fix-it", "Second Team", second));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(response)).isEqualTo("DUPLICATE_REGISTRATION");
        // The whole second team is rolled back, not just the offending member.
        assertThat(countRegistrations("fix-it")).isEqualTo(3);
    }

    @Test
    @DisplayName("two teams in one event cannot share a name")
    void teamNamesAreUniquePerEvent() {
        assertThat(register(TestRequests.team("ideathon", "Stack Overflow", "N1", 2, (short) 1))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> clash =
                register(TestRequests.team("ideathon", "stack overflow", "N2", 2, (short) 1));

        assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(clash)).isEqualTo("TEAM_NAME_TAKEN");
    }

    @Test
    @DisplayName("a replayed idempotency key returns the original registration, not a second one")
    void idempotencyKeyReplayIsSafe() {
        RegistrationRequest request = new RegistrationRequest(
                "chess", null, List.of(TestRequests.participant("1MS24CS300", (short) 1)), "key-abc");

        ResponseEntity<JsonNode> first = register(request);
        ResponseEntity<JsonNode> replay = register(request);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().get("registrations").get(0).get("registrationId").asLong())
                .isEqualTo(first.getBody().get("registrations").get(0).get("registrationId").asLong());
        assertThat(countRegistrations("chess")).isEqualTo(1);
    }

    // ------------------------------------------------------------ bad requests

    @Test
    @DisplayName("an unknown event id returns 404")
    void unknownEventIsNotFound() {
        ResponseEntity<JsonNode> response = register(
                TestRequests.solo("quidditch", "1MS24CS400", (short) 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codeOf(response)).isEqualTo("EVENT_NOT_FOUND");
    }

    @Test
    @DisplayName("a malformed payload returns 400 with per-field errors")
    void malformedPayloadIsRejected() {
        RegistrationRequest request = new RegistrationRequest("chess", null,
                List.of(new ParticipantRequest("", "", "not-an-email", (short) 7)), null);

        ResponseEntity<JsonNode> response = register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(response)).isEqualTo("VALIDATION_FAILED");
        JsonNode fieldErrors = detailsOf(response).get("fieldErrors");
        assertThat(fieldErrors.has("participants[0].rollNo")).isTrue();
        assertThat(fieldErrors.has("participants[0].email")).isTrue();
        assertThat(fieldErrors.has("participants[0].yearLevel")).isTrue();
    }

    @Test
    @DisplayName("an empty roster is rejected")
    void emptyRosterIsRejected() {
        ResponseEntity<JsonNode> response =
                register(new RegistrationRequest("chess", null, List.of(), null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(response)).isEqualTo("VALIDATION_FAILED");
    }

    // ------------------------------------------------------------ helpers

    private ResponseEntity<JsonNode> register(RegistrationRequest request) {
        return http.postForEntity("/api/registrations", request, JsonNode.class);
    }

    private String codeOf(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().get("code").asText();
    }

    private JsonNode detailsOf(ResponseEntity<JsonNode> response) {
        return response.getBody().get("details");
    }
}
