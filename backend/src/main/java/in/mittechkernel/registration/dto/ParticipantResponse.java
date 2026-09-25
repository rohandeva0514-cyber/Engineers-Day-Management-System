package in.mittechkernel.registration.dto;

import in.mittechkernel.registration.entity.Participant;

/** A participant as the API presents them. The JPA entity is never serialised directly. */
public record ParticipantResponse(
        Long participantId,
        String rollNo,
        String fullName,
        String email,
        short yearLevel) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getRollNo(),
                participant.getFullName(),
                participant.getEmail(),
                participant.getYearLevel());
    }
}
