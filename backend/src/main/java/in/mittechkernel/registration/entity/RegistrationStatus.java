package in.mittechkernel.registration.entity;

/**
 * Registration lifecycle for an event.
 *
 * <p>Only these three states exist in this milestone. Event execution states
 * (LIVE, SUBMISSION_OPEN, ...) are deliberately not modelled yet - registration and
 * execution are separate lifecycle concerns and execution is a later milestone.
 *
 * <p>{@link #REGISTRATION_OPEN} and {@link #REGISTRATION_CLOSED} are set by an operator.
 * {@link #SOLD_OUT} is normally <em>derived</em> from capacity rather than stored, so it
 * cannot drift out of sync with the seat count; see
 * {@link Event#effectiveRegistrationStatus()}. It remains a storable value so an operator
 * can also force it.
 */
public enum RegistrationStatus {
    REGISTRATION_OPEN,
    REGISTRATION_CLOSED,
    SOLD_OUT
}
