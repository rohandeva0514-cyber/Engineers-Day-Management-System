package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.ParticipantRequest;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every rule about who may enter an event and in what shape.
 *
 * <p>This class contains no knowledge of any specific event. It never mentions BuildX,
 * never checks for "tech-debate", never hardcodes 10 or 30. It reads the rules off the
 * {@link Event} row and applies them, which is what makes adding an event a migration
 * rather than a code change.
 *
 * <p>It has no repository dependencies either: everything it needs is passed in. That keeps
 * the rules unit-testable on their own and keeps the ordering of database work visible in
 * {@link RegistrationService} instead of hidden behind a validator.
 */
@Component
public class RegistrationRules {

    /**
     * Checks the request is a legal shape for this event, before any database work.
     *
     * <p>Order is chosen so the first error a student sees is the most useful one: an
     * unusable roster is reported before individual eligibility, because fixing the roster
     * may change who is on it.
     */
    public void validateRosterShape(Event event, String teamName, List<ParticipantRequest> roster) {
        rejectDuplicateRollNumbers(roster);

        if (event.isSolo()) {
            validateSoloShape(event, teamName, roster);
        } else {
            validateTeamShape(event, teamName, roster);
        }
    }

    private void validateSoloShape(Event event, String teamName, List<ParticipantRequest> roster) {
        if (roster.size() != 1) {
            throw new ApiException(ApiErrorCode.SOLO_EVENT_REJECTS_TEAM,
                    event.getName() + " is a solo event. Register exactly one participant.",
                    Map.of("eventId", event.getId(),
                           "submittedRosterSize", roster.size(),
                           "requiredRosterSize", 1));
        }
        if (teamName != null) {
            throw new ApiException(ApiErrorCode.SOLO_EVENT_REJECTS_TEAM,
                    event.getName() + " is a solo event and does not take a team name.",
                    Map.of("eventId", event.getId()));
        }
    }

    private void validateTeamShape(Event event, String teamName, List<ParticipantRequest> roster) {
        if (teamName == null) {
            throw new ApiException(ApiErrorCode.TEAM_NAME_REQUIRED,
                    event.getName() + " is a team event. A team name is required.",
                    Map.of("eventId", event.getId()));
        }

        int size = roster.size();
        if (size < event.getMinTeamSize() || size > event.getMaxTeamSize()) {
            throw new ApiException(ApiErrorCode.INVALID_TEAM_SIZE,
                    teamSizeMessage(event, size),
                    Map.of("eventId", event.getId(),
                           "submittedTeamSize", size,
                           "minTeamSize", event.getMinTeamSize(),
                           "maxTeamSize", event.getMaxTeamSize()));
        }
    }

    /**
     * Tech Debate's "exactly 10" deserves a different sentence from FIX IT's "1 to 4".
     * Both are the same rule - min and max - but a student reading "must be between 10 and
     * 10 members" learns nothing.
     */
    private String teamSizeMessage(Event event, int submitted) {
        if (event.requiresExactTeamSize()) {
            return event.getName() + " requires teams of exactly " + event.getMinTeamSize()
                    + " members. You submitted " + submitted + ".";
        }
        return event.getName() + " requires teams of " + event.getMinTeamSize() + " to "
                + event.getMaxTeamSize() + " members. You submitted " + submitted + ".";
    }

    private void rejectDuplicateRollNumbers(List<ParticipantRequest> roster) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ParticipantRequest participant : roster) {
            if (!seen.add(participant.normalisedRollNo())) {
                duplicates.add(participant.normalisedRollNo());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new ApiException(ApiErrorCode.DUPLICATE_PARTICIPANT_IN_ROSTER,
                    "The same participant appears more than once: " + String.join(", ", duplicates) + ".",
                    Map.of("rollNos", List.copyOf(duplicates)));
        }
    }

    /**
     * Year eligibility, decided from the <em>stored</em> participant records.
     *
     * <p>The year on the request is only ever used to create a participant who does not
     * exist yet. For anyone already known to the system the stored year wins, so a client
     * cannot claim to be a first year to reach BuildX.
     */
    public void validateEligibility(Event event, List<Participant> participants) {
        List<String> ineligible = participants.stream()
                .filter(participant -> !event.isYearEligible(participant.getYearLevel()))
                .map(Participant::getRollNo)
                .toList();

        if (!ineligible.isEmpty()) {
            throw ApiException.ineligibleYear(event.getId(), ineligible,
                    event.getEligibleYears().stream().sorted().toList());
        }
    }

    /** The event must be accepting registrations right now. */
    public void validateRegistrationOpen(Event event) {
        if (event.isOpenForRegistration()) {
            return;
        }
        if (event.isFull()) {
            throw ApiException.capacityFull(event.getId(), event.getCapacity(), event.getSeatsTaken());
        }
        throw ApiException.registrationClosed(event.getId(),
                event.effectiveRegistrationStatus().name());
    }
}
