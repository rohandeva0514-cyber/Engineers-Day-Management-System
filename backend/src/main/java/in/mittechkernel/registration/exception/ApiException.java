package in.mittechkernel.registration.exception;

import java.util.Map;

/**
 * The single exception type the domain throws to refuse a request.
 *
 * <p>One class with an {@link ApiErrorCode} rather than a dozen subclasses: the code is the
 * thing callers and clients branch on, and a class hierarchy whose only difference is which
 * constant it carries earns nothing.
 *
 * <p>{@code details} is structured data the client can render - the required team size, the
 * roll numbers that were already registered - so error screens do not have to parse prose.
 */
public class ApiException extends RuntimeException {

    private final transient ApiErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ApiErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    // ------------------------------------------------------------ factories

    public static ApiException eventNotFound(String eventId) {
        return new ApiException(ApiErrorCode.EVENT_NOT_FOUND,
                "No event exists with id '" + eventId + "'.",
                Map.of("eventId", eventId));
    }

    public static ApiException participantNotFound(String reference) {
        return new ApiException(ApiErrorCode.PARTICIPANT_NOT_FOUND,
                "No participant found for '" + reference + "'.",
                Map.of("reference", reference));
    }

    public static ApiException registrationClosed(String eventId, String status) {
        return new ApiException(ApiErrorCode.REGISTRATION_CLOSED,
                "Registration for '" + eventId + "' is not open.",
                Map.of("eventId", eventId, "registrationStatus", status));
    }

    public static ApiException capacityFull(String eventId, int capacity, int seatsTaken) {
        return new ApiException(ApiErrorCode.CAPACITY_FULL,
                "'" + eventId + "' is full. All " + capacity + " seats have been taken.",
                Map.of("eventId", eventId, "capacity", capacity, "seatsTaken", seatsTaken));
    }

    public static ApiException duplicateRegistration(String eventId, java.util.List<String> rollNos) {
        return new ApiException(ApiErrorCode.DUPLICATE_REGISTRATION,
                "Already registered for '" + eventId + "': " + String.join(", ", rollNos) + ".",
                Map.of("eventId", eventId, "rollNos", rollNos));
    }

    public static ApiException ineligibleYear(String eventId, java.util.List<String> rollNos,
                                              java.util.Collection<Short> eligibleYears) {
        return new ApiException(ApiErrorCode.INELIGIBLE_YEAR,
                "Not eligible for '" + eventId + "': " + String.join(", ", rollNos) + ".",
                Map.of("eventId", eventId, "rollNos", rollNos, "eligibleYears", eligibleYears));
    }
}
