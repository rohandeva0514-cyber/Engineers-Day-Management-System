package in.mittechkernel.registration.arena.bank;

/**
 * What is wrong with a problem's buggy code, where, and why it matters.
 *
 * <p>Pure answer material. This is the closest thing the bank has to a worked
 * solution in prose, and it is the single most damaging thing that could leak: a
 * participant holding {@code location} and {@code description} has been handed the
 * bug without doing any debugging.
 *
 * <p>It exists in the loaded model so that Phase E can explain results to
 * organisers afterwards, and so the bank stays a faithful representation of its
 * source. It is never serialised towards a participant, and no participant DTO has
 * a field it could be assigned to.
 */
public record BugNote(String location, String description, String whyWrong) {
}
