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
        Instant registeredAt,
        /**
         * What the FIRST participant on the roster may still register for.
         *
         * <p>The first entry is the captain for a team event and the sole entrant for a
         * solo one - in both cases the person holding the device that submitted this, and
         * so the one whose next step the success screen is about to describe.
         */
        RegistrationStateResponse registrationState) {

    /**
     * @param accessCode the event-day code, or null for events that issue none.
     *                   Per entry rather than per response because it authorises
     *                   one person for one event.
     */
    public record Entry(Long registrationId, ParticipantResponse participant, String accessCode) {
    }

    public static RegistrationResponse of(List<Registration> registrations,
                                          Team team,
                                          Integer seatsRemaining,
                                          RegistrationStateResponse registrationState) {
        Registration first = registrations.get(0);
        List<Entry> entries = registrations.stream()
                .map(reg -> new Entry(
                        reg.getId(),
                        ParticipantResponse.from(reg.getParticipant()),
                        reg.getAccessCode()))
                .toList();

        return new RegistrationResponse(
                first.getEvent().getId(),
                first.getEvent().getName(),
                first.getEvent().getParticipationType().name(),
                team == null ? null : TeamResponse.from(team),
                entries,
                seatsRemaining,
                first.getCreatedAt(),
                registrationState);
    }
}
