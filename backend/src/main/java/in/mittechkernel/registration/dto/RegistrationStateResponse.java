package in.mittechkernel.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What a student is still allowed to register for, slot by slot.
 *
 * <p>Returned alongside a registration and alongside a student's registration list so
 * the frontend can render the right call to action without re-deriving the rule. It is
 * a <em>report</em> of a decision the server has already made, never an input to one:
 * every registration is validated server-side regardless of what this said, so a client
 * that ignores it or forges it gains nothing.
 *
 * <p>One entry per slot, always, in slot order - including the ones still free. A client
 * rendering a three-slot strip needs the empty slots as much as the filled ones, and a
 * list that shrinks as it fills would make that strip the client's problem instead of
 * the server's.
 *
 * <p>The slots themselves are not named in the frontend: {@code slot} is the stable key
 * to match against an event's {@code registrationSlot}, and {@code label} is the text to
 * show. Adding a fourth group is then a backend change alone.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegistrationStateResponse(List<SlotState> slots) {

    /**
     * One registration group, and whether this student has used it.
     *
     * @param eventId    the event held in this slot, or null when it is free
     * @param eventName  the same event's name, so a refusal can say what is already
     *                   held rather than making the student go and look
     * @param canRegister true when the student may still enter an event in this slot.
     *                    The inverse of {@code taken} today; kept as its own field
     *                    because a slot could later be closed for reasons other than
     *                    being occupied, and clients should read the answer rather
     *                    than infer it.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SlotState(
            String slot,
            String label,
            boolean taken,
            String eventId,
            String eventName,
            boolean canRegister) {
    }
}
