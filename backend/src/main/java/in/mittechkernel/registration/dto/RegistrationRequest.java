package in.mittechkernel.registration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A complete registration, submitted in one call.
 *
 * <p>Team creation, member addition and registration are deliberately <em>one</em> request
 * rather than three. A multi-step flow leaves half-formed teams behind whenever a student
 * closes the tab, and with Tech Debate needing exactly ten members that failure mode would
 * dominate. Submitting the whole roster at once means a team either exists complete and
 * valid or does not exist at all - which is also what makes the seat claim honest, since a
 * team's seats are taken in the same transaction that creates it.
 *
 * <p>The first entry in {@code participants} is the captain. For a solo event the list holds
 * exactly one entry and {@code teamName} must be absent.
 *
 * @param idempotencyKey optional client-generated key. Replaying a request that already
 *                       succeeded returns the original registration instead of creating a
 *                       second one - which matters on campus wifi, where a request can
 *                       succeed and the response never arrive.
 */
public record RegistrationRequest(

        @NotBlank(message = "eventId is required")
        @Size(max = 32)
        String eventId,

        @Size(max = 120, message = "team name must be at most 120 characters")
        String teamName,

        @NotEmpty(message = "at least one participant is required")
        @Size(max = 20, message = "a roster may not exceed 20 participants")
        @Valid
        List<ParticipantRequest> participants,

        @Size(max = 80, message = "idempotency key must be at most 80 characters")
        String idempotencyKey) {

    public String normalisedEventId() {
        return eventId == null ? null : eventId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String normalisedTeamName() {
        if (teamName == null) {
            return null;
        }
        String trimmed = teamName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String normalisedIdempotencyKey() {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
