package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.ParticipantRequest;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.ParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves roll numbers on a roster to participant records, creating them on first sight.
 *
 * <p>There is no sign-up step in this milestone: a student exists because they registered
 * for something. That makes EMAIL the identity - it is unique per student and is the one
 * address they can be reached on - and makes this the place where identity is protected.
 *
 * <p>Roll number is an attribute, not a key. Two students may share one (see V7), so the
 * same roll number submitted with a different email is a different person, and the same
 * email submitted with a different roll number is the same person.
 */
@Service
public class ParticipantService {

    private final ParticipantRepository participantRepository;

    public ParticipantService(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    /**
     * Resolve every entry on a roster, preserving order so entry 0 stays the captain.
     *
     * <p>Existing participants are matched by EMAIL and the stored record wins. The
     * submitted year must agree with it, otherwise the request is refused: without that
     * check a returning student could flip their year and walk into an event their own
     * year is not eligible for.
     *
     * <p>Matching on email is what makes the registration limits hold. A student who
     * resubmits with a different roll number resolves to the same row, so changing it
     * cannot buy a second event in a slot they have already used.
     *
     * <p>Emails are looked up in one query rather than one per member, which matters for
     * a ten-member roster.
     */
    @Transactional
    public List<Participant> resolveAll(List<ParticipantRequest> roster) {
        List<String> emails = roster.stream()
                .map(request -> lowerEmail(request.normalisedEmail()))
                .toList();

        Map<String, Participant> existing =
                participantRepository.findAllByEmailInIgnoreCase(emails).stream()
                        .collect(Collectors.toMap(
                                participant -> lowerEmail(participant.getEmail()),
                                Function.identity()));

        List<Participant> resolved = new ArrayList<>(roster.size());
        List<Participant> toCreate = new ArrayList<>();

        for (ParticipantRequest request : roster) {
            Participant known = existing.get(lowerEmail(request.normalisedEmail()));
            if (known != null) {
                verifyMatchesStoredIdentity(request, known);
                // Completes records created before phone/branch/division were collected.
                // Only fills blanks - a later submission never overwrites what is on file.
                known.fillMissingProfile(
                        request.normalisedPhone(),
                        request.normalisedBranch(),
                        request.normalisedDivision());
                resolved.add(known);
            } else {
                Participant created = newParticipant(request);
                toCreate.add(created);
                resolved.add(created);
            }
        }

        if (!toCreate.isEmpty()) {
            // Flushed here so identity constraints fail now, inside the registration
            // transaction, rather than at an unpredictable point later.
            participantRepository.saveAllAndFlush(toCreate);
        }
        return resolved;
    }

    /**
     * The email already identified this record, so there is nothing to re-check about it.
     * Year is another matter: it decides eligibility, and the stored value is the one that
     * counts.
     */
    private void verifyMatchesStoredIdentity(ParticipantRequest request, Participant stored) {
        if (stored.getYearLevel() != request.yearLevel()) {
            throw new ApiException(ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
                    stored.getEmail() + " is on record as year "
                            + stored.getYearLevel() + ", not year " + request.yearLevel()
                            + ". Contact the organisers if this is wrong.",
                    Map.of("email", stored.getEmail(),
                           "field", "yearLevel",
                           "storedYearLevel", stored.getYearLevel(),
                           "submittedYearLevel", request.yearLevel()));
        }
    }

    /**
     * Only reached when no participant holds this email, because the caller looked it up
     * first. The unique index on lower(email) remains the real guarantee under a race.
     */
    private Participant newParticipant(ParticipantRequest request) {
        return new Participant(
                request.normalisedRollNo(),
                request.normalisedFullName(),
                request.normalisedEmail(),
                request.yearLevel(),
                request.normalisedPhone(),
                request.normalisedBranch(),
                request.normalisedDivision());
    }

    /**
     * Look a participant up by numeric id or by email.
     *
     * <p>Both exist because a client that has just registered holds the id, while a student
     * coming back on a different device knows their email address.
     *
     * <p>There is deliberately no lookup by roll number. Roll numbers repeat, so such a
     * lookup could return someone else's record - which on an unauthenticated endpoint
     * would hand one student another's registrations.
     */
    @Transactional(readOnly = true)
    public Participant requireById(Long participantId) {
        return participantRepository.findById(participantId)
                .orElseThrow(() -> ApiException.participantNotFound(String.valueOf(participantId)));
    }

    @Transactional(readOnly = true)
    public Participant requireByEmail(String email) {
        String normalised = Optional.ofNullable(email).orElse("").trim();
        return participantRepository.findByEmailIgnoreCase(normalised)
                .orElseThrow(() -> ApiException.participantNotFound(normalised));
    }

    private static String lowerEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
