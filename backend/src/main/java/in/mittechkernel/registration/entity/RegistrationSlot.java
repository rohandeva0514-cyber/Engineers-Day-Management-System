package in.mittechkernel.registration.entity;

/**
 * Which registration group an event belongs to.
 *
 * <p>A student may hold <em>at most one</em> registration per slot, so the three
 * constants are also the three events a student ends up with. Which slot an event
 * uses is stored per event rather than decided in code - see
 * V11__three_registration_slots.sql - so no class in this package needs to know
 * that FIX IT is the core event or that BuildX competes with Ideathon.
 *
 * <p>The rule is uniform across all three. {@link #CORE} holding a single event is
 * a property of the current catalogue, not a special case in the logic: "at most
 * one CORE registration" is enforced exactly the way the other two are.
 */
public enum RegistrationSlot {

    /** The event every student is expected to take. Currently FIX IT alone. */
    CORE("Core"),

    /** Make something. Currently BuildX or Ideathon. */
    BUILD("Build"),

    /** Compete. Currently Chess, Debugging, Tech Debate or Rapid Research. */
    CHALLENGE("Challenge");

    private final String label;

    RegistrationSlot(String label) {
        this.label = label;
    }

    /**
     * How this slot is named to a student.
     *
     * <p>Lives here rather than in the frontend so a refusal message and the slot
     * strip on the events page cannot drift apart, and so the client never has to
     * keep its own table of slot names in step with this enum.
     */
    public String label() {
        return label;
    }
}
