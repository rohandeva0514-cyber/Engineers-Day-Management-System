package in.mittechkernel.registration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeded event catalogue.
 *
 * <p>This asserts the migration against CLAUDE.md directly. The rules live in data, so a
 * mistake in the seed is a product bug that no amount of correct Java would catch - a wrong
 * {@code max_team_size} here would let five people into a four-person Ideathon team and every
 * other test would still pass.
 */
class EventCatalogApiTest extends PostgresIntegrationTest {

    @Test
    @DisplayName("all seven events are seeded and listed in display order")
    void allEventsAreSeeded() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/events", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(7);

        List<String> ids = response.getBody().findValuesAsText("eventId");
        assertThat(ids).containsExactly(
                "chess", "tech-debate", "fix-it", "ideathon",
                "buildx", "debugging", "rapid-research");
    }

    @ParameterizedTest(name = "{0}: {1}, team {2}-{3}, capacity {4}, slot {6}")
    @CsvSource(nullValues = "null", value = {
            // eventId,        type, min, max, capacity, eligibleYears, slot
            "chess,            SOLO,  1,  1,  null, '1,2', CHALLENGE",
            // Temporarily SOLO - see V4__tech_debate_solo.sql.
            "tech-debate,      SOLO,  1,  1,  null, '1,2', CHALLENGE",
            "fix-it,           TEAM,  1,  4,  null, '1,2', CORE",
            "ideathon,         TEAM,  1,  4,    40, '1,2', BUILD",
            // Opened to both years by V11__three_registration_slots.sql.
            "buildx,           SOLO,  1,  1,    30, '1,2', BUILD",
            "debugging,        SOLO,  1,  1,  null, '1,2', CHALLENGE",
            // Capped at two - see V10__rapid_research_max_two.sql.
            "rapid-research,   TEAM,  1,  2,  null, '2',   CHALLENGE"
    })
    @DisplayName("each event matches the rules in CLAUDE.md")
    void eventRulesMatchSpecification(String eventId, String participationType,
                                      int minTeamSize, int maxTeamSize,
                                      Integer capacity, String eligibleYears,
                                      String registrationSlot) {
        ResponseEntity<JsonNode> response =
                http.getForEntity("/api/events/" + eventId, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode event = response.getBody();
        assertThat(event).isNotNull();

        assertThat(event.get("participationType").asText()).isEqualTo(participationType);
        assertThat(event.get("minTeamSize").asInt()).isEqualTo(minTeamSize);
        assertThat(event.get("maxTeamSize").asInt()).isEqualTo(maxTeamSize);

        if (capacity == null) {
            assertThat(event.get("capacity").isNull()).isTrue();
        } else {
            assertThat(event.get("capacity").asInt()).isEqualTo(capacity);
        }

        List<String> years = new ArrayList<>();
        event.get("eligibleYears").forEach(year -> years.add(year.asText()));
        assertThat(String.join(",", years)).isEqualTo(eligibleYears);

        assertThat(event.get("registrationStatus").asText()).isEqualTo("REGISTRATION_OPEN");

        // The slot decides which events compete with each other, so a wrong value here
        // is a product bug no Java test would otherwise catch: it would silently let a
        // student hold both BuildX and Ideathon.
        assertThat(event.get("registrationSlot").asText()).isEqualTo(registrationSlot);
        assertThat(event.get("registrationSlotLabel").asText()).isNotBlank();
    }

    @Test
    @DisplayName("the catalogue offers exactly one CORE event and a choice in the others")
    void slotsPartitionTheCatalogue() {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/events", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> slots = response.getBody().findValuesAsText("registrationSlot");

        // Three groups, every event in exactly one of them, and none of them empty -
        // an event stranded outside a slot would be unreachable to every student who
        // had already registered for anything.
        assertThat(slots).hasSize(7);
        assertThat(slots).filteredOn("CORE"::equals).hasSize(1);
        assertThat(slots).filteredOn("BUILD"::equals).hasSize(2);
        assertThat(slots).filteredOn("CHALLENGE"::equals).hasSize(4);
    }

    @Test
    @DisplayName("an event id is matched case-insensitively")
    void eventIdIsCaseInsensitive() {
        assertThat(http.getForEntity("/api/events/BuildX", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an unknown event returns a 404 in the standard error shape")
    void unknownEventReturns404() {
        ResponseEntity<JsonNode> response =
                http.getForEntity("/api/events/quidditch", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode error = response.getBody();
        assertThat(error.get("code").asText()).isEqualTo("EVENT_NOT_FOUND");
        assertThat(error.get("status").asInt()).isEqualTo(404);
        assertThat(error.get("message").asText()).contains("quidditch");
        assertThat(error.has("timestamp")).isTrue();
        assertThat(error.get("path").asText()).isEqualTo("/api/events/quidditch");
    }

    @Test
    @DisplayName("an unknown participant returns 404 rather than an empty list")
    void unknownParticipantReturns404() {
        ResponseEntity<JsonNode> response =
                http.getForEntity("/api/registrations/999999", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asText()).isEqualTo("PARTICIPANT_NOT_FOUND");
    }
}
