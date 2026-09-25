package in.mittechkernel.registration.dto;

import in.mittechkernel.registration.entity.Participant;

/** A participant as the API presents them. The JPA entity is never serialised directly. */
public record ParticipantResponse(
        Long participantId,
        String rollNo,
        String fullName,
        String email,
        short yearLevel,
        /** Null for participants created before these were collected. */
        String phone,
        String branch,
        String division) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getRollNo(),
                participant.getFullName(),
                participant.getEmail(),
                participant.getYearLevel(),
                participant.getPhone(),
                participant.getBranch(),
                participant.getDivision());
    }
}
