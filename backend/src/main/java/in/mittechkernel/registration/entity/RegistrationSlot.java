package in.mittechkernel.registration.entity;

/**
 * Which registration slot an event consumes.
 *
 * <p>A student holds at most one {@link #PRIMARY} registration, and may hold an
 * {@link #OPEN} one alongside it. The distinction is stored per event rather than
 * decided in code, so no class in this package needs to know that FIX IT is the
 * open event - or that it is the only one.
 */
public enum RegistrationSlot {
    /** Consumes the single primary slot. Holding one blocks every other PRIMARY event. */
    PRIMARY,
    /** Consumes no primary slot. Can be held on its own or alongside a primary event. */
    OPEN
}
