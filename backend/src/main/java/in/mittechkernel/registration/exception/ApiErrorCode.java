package in.mittechkernel.registration.exception;

import org.springframework.http.HttpStatus;

/**
 * Every way a request can be refused, and the status it maps to.
 *
 * <p>Clients branch on {@code code}, never on the message text and never on the bare status.
 * That is what lets the registration UI show a real "BuildX just sold out" screen instead of
 * a generic error toast.
 *
 * <p>Status convention:
 * <ul>
 *   <li><b>400</b> - the request is malformed: missing fields, wrong types, bad formats.</li>
 *   <li><b>404</b> - the thing addressed does not exist.</li>
 *   <li><b>409</b> - the request is well-formed but conflicts with current server state
 *       (closed, sold out, already registered). Retrying later could succeed or fail
 *       differently.</li>
 *   <li><b>422</b> - the request is well-formed but breaks a rule of the event itself
 *       (wrong year, wrong team size). Retrying identically will always fail.</li>
 * </ul>
 */
public enum ApiErrorCode {

    /** Bean Validation rejected the payload shape. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** Body could not be parsed, or a path/query value had the wrong type. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * The access code presented at check-in matches no registration.
     *
     * <p>Deliberately says nothing about WHY - whether the code never existed, was
     * for a different event, or was mistyped. A verification endpoint that
     * distinguishes those cases tells someone probing it when they are close.
     */
    ACCESS_CODE_INVALID(HttpStatus.NOT_FOUND),

    /** The event is not accepting registrations (operator-closed). */
    REGISTRATION_CLOSED(HttpStatus.CONFLICT),

    /**
     * The master switch is off, so nothing is being accepted for any event.
     *
     * <p>Separate from REGISTRATION_CLOSED so a student is not told "this event is
     * closed" when in fact the whole system is paused, and so the panel can tell the
     * two apart at a glance.
     */
    REGISTRATION_SYSTEM_CLOSED(HttpStatus.CONFLICT),

    /** No seats left. BuildX at 30, or Ideathon at its configured capacity. */
    CAPACITY_FULL(HttpStatus.CONFLICT),

    /** At least one person on the roster already holds a registration for this event. */
    DUPLICATE_REGISTRATION(HttpStatus.CONFLICT),

    /**
     * The student already holds an event in this one's registration group.
     *
     * <p>Distinct from DUPLICATE_REGISTRATION on purpose: that one means "you are
     * already in this event", this one means "you are already in a different event
     * that competes with it". The student needs to be told which, and that the
     * other groups are untouched.
     */
    EVENT_SLOT_ALREADY_TAKEN(HttpStatus.CONFLICT),

    /** Another team in this event already uses that name. */
    TEAM_NAME_TAKEN(HttpStatus.CONFLICT),

    /**
     * The roll number exists but the supplied email or year does not match the stored record.
     * Refusing here stops one student registering under another's roll number, and stops a
     * typo silently changing the year that eligibility is decided from.
     */
    PARTICIPANT_IDENTITY_CONFLICT(HttpStatus.CONFLICT),

    /** The participant's year is not eligible for this event. */
    INELIGIBLE_YEAR(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Roster size is outside the event's min/max - including Tech Debate's exactly 10. */
    INVALID_TEAM_SIZE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** A solo event was sent more than one participant, or a team name. */
    SOLO_EVENT_REJECTS_TEAM(HttpStatus.UNPROCESSABLE_ENTITY),

    /** A team event was sent no team name. */
    TEAM_NAME_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** The same roll number appears twice on one roster. */
    DUPLICATE_PARTICIPANT_IN_ROSTER(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
