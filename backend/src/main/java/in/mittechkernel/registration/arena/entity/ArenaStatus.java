package in.mittechkernel.registration.arena.entity;

/**
 * The Debugging Arena's lifecycle.
 *
 * <p>Three states, not a boolean, because STOP and END are different operations an
 * organiser needs to be able to choose between:
 *
 * <ul>
 *   <li>{@link #OFFLINE} - nobody may check in. Existing attempts are frozen, not
 *       destroyed. This is "pause, something is wrong, I will resume".</li>
 *   <li>{@link #ACTIVE} - the competition is running. The only state in which an
 *       attempt may start, run code, or submit.</li>
 *   <li>{@link #ENDED} - the event is over. Remaining attempts are finalized from
 *       their last saved draft.</li>
 * </ul>
 *
 * <p>Collapsing the first and third into one "closed" state would mean an organiser
 * pausing for a power cut and an organiser closing the event press the same button,
 * and one of those finalizes everyone's work irreversibly.
 */
public enum ArenaStatus {

    OFFLINE,
    ACTIVE,
    ENDED;

    /**
     * Whether this status may move to {@code target}.
     *
     * <p>The table, and why each entry is what it is:
     *
     * <pre>
     *   OFFLINE -> ACTIVE    start the arena
     *   OFFLINE -> ENDED     paused, then decided the event is over
     *   ACTIVE  -> OFFLINE   pause
     *   ACTIVE  -> ENDED     finish
     *   ENDED   -> OFFLINE   reopen, deliberately via OFFLINE
     *   ENDED   -> ACTIVE    REFUSED
     * </pre>
     *
     * <p>{@code ENDED -> ACTIVE} is the one refusal that matters. Reopening a
     * finished event has to be a two-step decision, because the one-step version is
     * a mis-click that puts a closed competition back in front of students whose
     * attempts have already been finalized.
     *
     * <p>A transition to the current status is allowed and does nothing. An admin
     * double-clicking "Start", or a retried request, should not be an error.
     */
    public boolean canTransitionTo(ArenaStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case OFFLINE -> target == ACTIVE || target == ENDED;
            case ACTIVE -> target == OFFLINE || target == ENDED;
            case ENDED -> target == OFFLINE;
        };
    }
}
