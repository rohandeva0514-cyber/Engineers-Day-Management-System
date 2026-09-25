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
 * for something. That makes roll number the identity, and makes this the place where
 * identity is protected.
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
     * <p>Existing participants are matched by roll number and the stored record wins. The
     * submitted email and year must agree with it, otherwise the request is refused: without
     * that check, anyone who knows a classmate's roll number could register them under a
     * different email, or flip a second year to "year 1" and walk into BuildX.
     *
     * <p>Roll numbers are looked up in one query rather than one per member, which matters
     * for a ten-member Debate roster.
     */
    @Transactional
    public List<Participant> resolveAll(List<ParticipantRequest> roster) {
        List<String> rollNos = roster.stream().map(ParticipantRequest::normalisedRollNo).toList();

        Map<String, Participant> existing = participantRepository.findAllByRollNoIn(rollNos)
                .stream()
                .collect(Collectors.toMap(Participant::getRollNo, Function.identity()));

        List<Participant> resolved = new ArrayList<>(roster.size());
        List<Participant> toCreate = new ArrayList<>();

        for (ParticipantRequest request : roster) {
            Participant known = existing.get(request.normalisedRollNo());
            if (known != null) {
                verifyMatchesStoredIdentity(request, known);
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

    private void verifyMatchesStoredIdentity(ParticipantRequest request, Participant stored) {
        if (!stored.getEmail().equalsIgnoreCase(request.normalisedEmail())) {
            throw new ApiException(ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
                    "Roll number " + stored.getRollNo() + " is already registered with a "
                            + "different email address.",
                    Map.of("rollNo", stored.getRollNo(), "field", "email"));
        }
        if (stored.getYearLevel() != request.yearLevel()) {
            throw new ApiException(ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
                    "Roll number " + stored.getRollNo() + " is on record as year "
                            + stored.getYearLevel() + ", not year " + request.yearLevel()
                            + ". Contact the organisers if this is wrong.",
                    Map.of("rollNo", stored.getRollNo(),
                           "field", "yearLevel",
                           "storedYearLevel", stored.getYearLevel(),
                           "submittedYearLevel", request.yearLevel()));
        }
    }

    private Participant newParticipant(ParticipantRequest request) {
        participantRepository.findByEmailIgnoreCase(request.normalisedEmail())
                .ifPresent(owner -> {
                    throw new ApiException(ApiErrorCode.PARTICIPANT_IDENTITY_CONFLICT,
                            "That email address is already registered to roll number "
                                    + owner.getRollNo() + ".",
                            Map.of("email", request.normalisedEmail(),
                                   "field", "email",
                                   "ownedByRollNo", owner.getRollNo()));
                });

        return new Participant(
                request.normalisedRollNo(),
                request.normalisedFullName(),
                request.normalisedEmail(),
                request.yearLevel());
    }

    /**
     * Look a participant up by numeric id or by roll number.
     *
     * <p>Both exist because a client that has just registered holds the id, while a student
     * coming back on a different device only knows their roll number.
     */
    @Transactional(readOnly = true)
    public Participant requireById(Long participantId) {
        return participantRepository.findById(participantId)
                .orElseThrow(() -> ApiException.participantNotFound(String.valueOf(participantId)));
    }

    @Transactional(readOnly = true)
    public Participant requireByRollNo(String rollNo) {
        String normalised = Optional.ofNullable(rollNo).orElse("").trim().toUpperCase(Locale.ROOT);
        return participantRepository.findByRollNo(normalised)
                .orElseThrow(() -> ApiException.participantNotFound(rollNo));
    }
}
