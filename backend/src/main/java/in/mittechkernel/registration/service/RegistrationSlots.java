package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.RegistrationStateResponse;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The one place the "one primary event, plus FIX IT" rule lives.
 *
 * <p>The whole rule is two slots:
 *
 * <pre>
 *   PRIMARY  at most one registration for an event marked PRIMARY
 *   OPEN     at most one registration for an event marked OPEN
 * </pre>
 *
 * <p>Which slot an event uses is read from the event row, so nothing here names FIX IT
 * or any other event. Opening a second event alongside FIX IT, or closing FIX IT back
 * into the primary pool, is a data change - see V6__event_registration_slot.sql.
 *
 * <p>Deliberately one class rather than checks scattered through the controller, the
 * service and the form. There is exactly one function that decides whether a slot is
 * free, and both the enforcement path and the "what can I still do" report call it - so
 * the UI and the server can never disagree about the rule, only about how recently they
 * looked.
 *
 * <p>Duplicate registration for the <em>same</em> event is NOT this class's job. That is
 * still the UNIQUE (event_id, participant_id) index and the check that reads it, which
 * is what lets a student hold a primary event and FIX IT at the same time.
 */
@Service
public class RegistrationSlots {

    private final RegistrationRepository registrationRepository;

    public RegistrationSlots(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * Refuse the request if any member of the roster has no free slot for this event.
     *
     * <p>Checked per member, not just for the captain: a team registration writes a row
     * for every person on it, so one member who already holds a primary event must stop
     * the whole submission rather than silently gaining a second one.
     *
     * <p>Only PRIMARY events can exhaust a slot this way. An OPEN event is refused only
     * when it is already held, and that refusal belongs to the duplicate check.
     */
    @Transactional(readOnly = true)
    public void validateSlotAvailable(Event event, List<Participant> roster) {
        if (event.isOpenSlot()) {
            return;
        }

        for (Participant participant : roster) {
            Registration held = firstPrimaryRegistration(participant.getId());
            if (held == null) {
                continue;
            }

            // Registering again for the SAME event is a duplicate, not a slot clash.
            // Letting the duplicate check answer it keeps that message accurate.
            if (held.getEvent().getId().equals(event.getId())) {
                continue;
            }

            throw new ApiException(ApiErrorCode.PRIMARY_EVENT_ALREADY_TAKEN,
                    participant.getRollNo() + " is already registered for "
                            + held.getEvent().getName()
                            + ". Only one main event is allowed per student, but FIX IT can "
                            + "still be entered alongside it.",
                    Map.of("rollNo", participant.getRollNo(),
                           "requestedEventId", event.getId(),
                           "heldEventId", held.getEvent().getId(),
                           "heldEventName", held.getEvent().getName()));
        }
    }

    /** What this student may still register for. */
    @Transactional(readOnly = true)
    public RegistrationStateResponse stateFor(Long participantId) {
        return stateFrom(registrationRepository.findAllByParticipantId(participantId));
    }

    /**
     * The same report, computed from registrations already loaded.
     *
     * <p>Used on the write path, where the rows were just created inside the open
     * transaction and a re-read would be both wasteful and - before commit - a source of
     * confusion about what is actually visible.
     */
    public RegistrationStateResponse stateFrom(List<Registration> registrations) {
        Registration primary = null;
        Registration open = null;

        for (Registration registration : registrations) {
            if (registration.getEvent().isOpenSlot()) {
                if (open == null) open = registration;
            } else if (primary == null) {
                primary = registration;
            }
        }

        return new RegistrationStateResponse(
                primary != null,
                primary == null ? null : primary.getEvent().getId(),
                primary == null ? null : primary.getEvent().getName(),
                open != null,
                open == null ? null : open.getEvent().getId(),
                open == null ? null : open.getEvent().getName(),
                primary == null,
                open == null);
    }

    private Registration firstPrimaryRegistration(Long participantId) {
        return registrationRepository.findAllByParticipantId(participantId).stream()
                .filter(registration -> registration.getEvent().isPrimarySlot())
                .findFirst()
                .orElse(null);
    }
}
