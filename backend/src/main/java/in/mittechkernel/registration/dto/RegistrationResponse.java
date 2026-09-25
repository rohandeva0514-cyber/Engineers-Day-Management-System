package in.mittechkernel.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.entity.Team;

import java.time.Instant;
import java.util.List;

/**
 * The result of a successful registration.
 *
 * <p>Carries one entry per person, each with its own {@code registrationId}, because a team
 * of ten produces ten registrations. {@code participantId} is returned so a client can store
 * it and later call {@code GET /api/registrations/{participantId}} without a lookup.
 *
 * <p>{@code seatsRemaining} is included so the client can update its view without a second
 * request - and because the number a student saw at the moment they registered is worth
 * showing them.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegistrationResponse(
        String eventId,
        String eventName,
        String participationType,
        TeamResponse team,
        List<Entry> registrations,
        Integer seatsRemaining,
        Instant registeredAt) {

    public record Entry(Long registrationId, ParticipantResponse participant) {
    }

    public static RegistrationResponse of(List<Registration> registrations,
                                          Team team,
                                          Integer seatsRemaining) {
        Registration first = registrations.get(0);
        List<Entry> entries = registrations.stream()
                .map(reg -> new Entry(reg.getId(), ParticipantResponse.from(reg.getParticipant())))
                .toList();

        return new RegistrationResponse(
                first.getEvent().getId(),
                first.getEvent().getName(),
                first.getEvent().getParticipationType().name(),
                team == null ? null : TeamResponse.from(team),
                entries,
                seatsRemaining,
                first.getCreatedAt());
    }
}
