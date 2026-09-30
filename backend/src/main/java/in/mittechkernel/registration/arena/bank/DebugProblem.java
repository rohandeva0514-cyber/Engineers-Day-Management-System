package in.mittechkernel.registration.arena.bank;

import in.mittechkernel.registration.arena.ArenaLanguage;

import java.util.List;

/**
 * One problem, complete - including everything a participant must never see.
 *
 * <p><b>This type never leaves the server.</b> It is the loaded form of a bank
 * entry, and it carries {@link #correctedCode()}, {@link #bugs()} and
 * {@link #hiddenTests()} precisely so that the judge in Phase D has them. No
 * controller returns it, no DTO embeds it, and no Jackson writer is ever pointed at
 * it. The participant-facing shapes are built by {@code ProblemMapper} from a
 * deliberately narrow subset.
 *
 * <p>The protection is not "remember not to return this". It is that the DTOs have
 * no field capable of holding any of it, and a test serialises every response for
 * all 60 problems and fails on the presence of any answer material.
 *
 * @param id          the bank's own identifier, e.g. {@code C++-E-001}
 * @param ref         URL-safe board handle within a language, e.g. {@code E-01}
 * @param ordinal     position within its difficulty zone, 1-4
 * @param buggyCode   participant-visible: this is the thing they are asked to fix
 * @param bugs        SERVER ONLY - what is wrong, where, and why
 * @param correctedCode SERVER ONLY - the reference solution
 * @param visibleTests participant-visible worked examples
 * @param hiddenTests SERVER ONLY - what a submission is actually judged against
 */
public record DebugProblem(
        String id,
        String ref,
        int ordinal,
        String title,
        ArenaLanguage language,
        Difficulty difficulty,
        int points,
        int bugCount,
        List<String> concepts,
        String problemStatement,
        String inputFormat,
        String outputFormat,
        String constraints,
        String buggyCode,
        List<BugNote> bugs,
        String correctedCode,
        List<TestCase> visibleTests,
        List<TestCase> hiddenTests,
        int estimatedTimeMinutes) {

    public DebugProblem {
        concepts = List.copyOf(concepts);
        bugs = List.copyOf(bugs);
        visibleTests = List.copyOf(visibleTests);
        hiddenTests = List.copyOf(hiddenTests);
    }
}
