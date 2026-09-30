package in.mittechkernel.registration.arena.entity;

/**
 * One participant's progress through the arena.
 *
 * <pre>
 *   (no row) ──access exchange──▶ INITIALIZED ──start──▶ ACTIVE ──▶ SUBMITTED
 *                                 clock stopped          clock running    EXPIRED
 *                                                                         TERMINATED
 * </pre>
 *
 * <p>{@link #INITIALIZED} costs a student nothing: no clock, no consumed attempt,
 * language still free to change. It exists so a session has somewhere to live from
 * the first check-in, and so the one-attempt uniqueness binds from that moment
 * rather than only after Start Mission.
 *
 * <p>The three terminal states are distinguished because a student needs to be told
 * which happened - "you submitted", "time ran out", "mission control ended the
 * arena" are three different things to read on a screen - and because Phase E
 * scores all three from the last saved draft but records why.
 */
public enum AttemptState {

    /** Checked in. Clock not started; language may still change. */
    INITIALIZED,

    /** Mission running. {@code startedAt}, {@code expiresAt} and language are set. */
    ACTIVE,

    /** The participant finalized their own attempt. Phase E. */
    SUBMITTED,

    /** The 45 minutes ran out. Phase E. */
    EXPIRED,

    /** Mission control ended the arena, or an admin stopped this attempt. Phase E. */
    TERMINATED;

    /** True once nothing further may be changed. */
    public boolean isFinal() {
        return this == SUBMITTED || this == EXPIRED || this == TERMINATED;
    }

    /** True while the mission clock is running. */
    public boolean isRunning() {
        return this == ACTIVE;
    }
}
