package in.mittechkernel.registration.dto;

import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;

import java.time.Instant;
import java.util.List;

/**
 * Everything one student is registered for.
 *
 * <p>This is what a "my registrations" screen renders, and what a student is asked for at
 * the desk on event day.
 */
public record ParticipantRegistrationsResponse(
        ParticipantResponse participant,
        List<Item> registrations,
        /** What this student may still register for. Lets the UI avoid re-deriving it. */
        RegistrationStateResponse registrationState) {

    public record Item(
            Long registrationId,
            String eventId,
            String eventName,
            String participationType,
            Long teamId,
            String teamName,
            Instant registeredAt) {
    }

    public static ParticipantRegistrationsResponse of(Participant participant,
                                                      List<Registration> registrations,
                                                      RegistrationStateResponse state) {
        List<Item> items = registrations.stream()
                .map(reg -> new Item(
                        reg.getId(),
                        reg.getEvent().getId(),
                        reg.getEvent().getName(),
                        reg.getEvent().getParticipationType().name(),
                        reg.getTeam() == null ? null : reg.getTeam().getId(),
                        reg.getTeam() == null ? null : reg.getTeam().getName(),
                        reg.getCreatedAt()))
                .toList();

        return new ParticipantRegistrationsResponse(
                ParticipantResponse.from(participant), items, state);
    }
}
