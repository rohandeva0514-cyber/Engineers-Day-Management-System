package in.mittechkernel.registration.arena.entity;

/**
 * What a participant has done with one problem.
 *
 * <pre>
 *   NOT_ATTEMPTED  ○   untouched
 *   ATTEMPTED      ◐   a draft has been saved
 *   SOLVED         ✓   every test passed, on the server
 * </pre>
 *
 * <p>{@link #SOLVED} is <b>not reachable in Phase C</b>, and that is deliberate
 * rather than incomplete. It means "the hidden tests passed", which requires
 * actually running the code - Phase D. Marking a problem solved because someone
 * typed into the editor, or because a client asked nicely, would put a badge on the
 * board that the scoring pass would later contradict.
 *
 * <p>Nor is it reachable by clicking Run, once Run exists: it is set from the result
 * the judge returns, and it can move back to {@link #ATTEMPTED} if a later edit
 * breaks the fix, because the stored draft is what gets scored.
 */
public enum AttemptProblemStatus {

    NOT_ATTEMPTED,
    ATTEMPTED,
    SOLVED
}
