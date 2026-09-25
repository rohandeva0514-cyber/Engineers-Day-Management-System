package in.mittechkernel.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a student is still allowed to register for.
 *
 * <p>Returned alongside a registration and alongside a student's registration list so
 * the frontend can render the right call to action without re-deriving the rule. It is
 * a <em>report</em> of a decision the server has already made, never an input to one:
 * every registration is validated server-side regardless of what this said, so a client
 * that ignores it or forges it gains nothing.
 *
 * <p>{@code primaryEventId} and {@code primaryEventName} are null when no primary event
 * is held. They exist so a refusal can name the event the student is already in rather
 * than making them go and look.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegistrationStateResponse(
        boolean hasPrimaryEvent,
        String primaryEventId,
        String primaryEventName,
        boolean hasOpenEvent,
        String openEventId,
        String openEventName,
        boolean canRegisterPrimaryEvent,
        boolean canRegisterOpenEvent) {
}
