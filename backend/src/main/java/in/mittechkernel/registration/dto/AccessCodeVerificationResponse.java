package in.mittechkernel.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;

/**
 * What a marshal sees when a student presents an access code.
 *
 * <p>Six fields, and the list is the specification rather than a subset that
 * happened to be convenient: name, branch, division, year, event, status. Enough
 * to confirm the person in front of you is the person the code belongs to.
 *
 * <p>Deliberately ABSENT: email, phone number, roll number and the internal
 * participant id. This endpoint is public - it has to be, a student types their
 * code at a terminal - so anything returned here is effectively returned to
 * anyone holding a code. Contact details are not needed to check someone in, so
 * they are not sent.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccessCodeVerificationResponse(
        String fullName,
        String branch,
        String division,
        short yearLevel,
        String eventId,
        String eventName,
        String registrationStatus) {

    public static AccessCodeVerificationResponse from(Registration registration) {
        Participant participant = registration.getParticipant();
        return new AccessCodeVerificationResponse(
                participant.getFullName(),
                participant.getBranch(),
                participant.getDivision(),
                participant.getYearLevel(),
                registration.getEvent().getId(),
                registration.getEvent().getName(),
                "REGISTERED");
    }
}
