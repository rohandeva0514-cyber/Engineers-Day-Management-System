package in.mittechkernel.registration.service;

import in.mittechkernel.registration.dto.RegistrationStateResponse;
import in.mittechkernel.registration.dto.RegistrationStateResponse.SlotState;
import in.mittechkernel.registration.entity.Event;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;
import in.mittechkernel.registration.entity.RegistrationSlot;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import in.mittechkernel.registration.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The one place the "one event per slot" rule lives.
 *
 * <p>The whole rule is a single sentence applied to every group alike:
 *
 * <pre>
 *   at most one registration per {@link RegistrationSlot}
 * </pre>
 *
 * <p>Which slot an event belongs to is read from the event row, so nothing here names
 * FIX IT, BuildX or any other event, and nothing here knows that CORE currently holds a
 * single event. Moving an event between groups, or adding a fourth group, is a data
 * change plus an enum constant - see V11__three_registration_slots.sql.
 *
 * <p>Deliberately one class rather than checks scattered through the controller, the
 * service and the form. There is exactly one function that decides whether a slot is
 * free, and both the enforcement path and the "what can I still do" report call it - so
 * the UI and the server can never disagree about the rule, only about how recently they
 * looked.
 *
 * <p>Duplicate registration for the <em>same</em> event is NOT this class's job. That is
 * still the UNIQUE (event_id, participant_id) index and the check that reads it. Keeping
 * them apart is what makes the messages accurate: "you are already in this event" and
 * "you are already in a different event in this group" are different problems with
 * different fixes.
 */
@Service
public class RegistrationSlots {

    private final RegistrationRepository registrationRepository;

    public RegistrationSlots(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * Refuse the request if any member of the roster has already used this event's slot.
     *
     * <p>Checked per member, not just for the captain: a team registration writes a row
     * for every person on it, so one member who has already spent the slot must stop the
     * whole submission rather than silently gaining a second event in the same group.
     */
    @Transactional(readOnly = true)
    public void validateSlotAvailable(Event event, List<Participant> roster) {
        RegistrationSlot slot = event.getRegistrationSlot();

        for (Participant participant : roster) {
            Registration held = heldInSlot(participant.getId(), slot);
            if (held == null) {
                continue;
            }

            // Registering again for the SAME event is a duplicate, not a slot clash.
            // Letting the duplicate check answer it keeps that message accurate.
            if (held.getEvent().getId().equals(event.getId())) {
                continue;
            }

            throw new ApiException(ApiErrorCode.EVENT_SLOT_ALREADY_TAKEN,
                    participant.getRollNo() + " is already registered for "
                            + held.getEvent().getName() + ". Only one "
                            + slot.label() + " event is allowed per student.",
                    Map.of("rollNo", participant.getRollNo(),
                           "slot", slot.name(),
                           "slotLabel", slot.label(),
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
        Map<RegistrationSlot, Registration> held = new EnumMap<>(RegistrationSlot.class);
        for (Registration registration : registrations) {
            held.putIfAbsent(registration.getEvent().getRegistrationSlot(), registration);
        }

        // Every slot is reported, free ones included, in declaration order. The client
        // renders the strip straight from this rather than reconciling it against the
        // catalogue.
        List<SlotState> slots = Arrays.stream(RegistrationSlot.values())
                .map(slot -> toSlotState(slot, held.get(slot)))
                .toList();

        return new RegistrationStateResponse(slots);
    }

    private SlotState toSlotState(RegistrationSlot slot, Registration held) {
        return new SlotState(
                slot.name(),
                slot.label(),
                held != null,
                held == null ? null : held.getEvent().getId(),
                held == null ? null : held.getEvent().getName(),
                held == null);
    }

    private Registration heldInSlot(Long participantId, RegistrationSlot slot) {
        return registrationRepository.findAllByParticipantId(participantId).stream()
                .filter(registration -> registration.getEvent().getRegistrationSlot() == slot)
                .findFirst()
                .orElse(null);
    }
}
