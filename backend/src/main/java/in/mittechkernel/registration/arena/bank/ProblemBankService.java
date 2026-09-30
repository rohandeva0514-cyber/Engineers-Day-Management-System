package in.mittechkernel.registration.arena.bank;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.ProblemBankLoader.ProblemBankException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The problem bank, loaded once and held immutably.
 *
 * <h2>Fixed, not generated</h2>
 *
 * <p>There is no randomness anywhere in this class. Every participant debugging in
 * a given language receives the same twelve problems in the same order, because the
 * event has to be comparable across participants. No seed, no shuffle, no
 * per-participant selection, no database copy.
 *
 * <h2>Validated at startup, or the application does not start</h2>
 *
 * <p>{@link #verify()} runs from {@code @PostConstruct}. A bank that is short a
 * problem, mislabels a difficulty, or reuses an id fails the deploy rather than the
 * event - the alternative is discovering it when thirty students open a board with
 * eleven problems on it, at which point there is nothing anyone can do.
 *
 * <h2>Two surfaces, one of which is not for participants</h2>
 *
 * <p>{@link #board(ArenaLanguage)} and {@link #find} return the full
 * {@link DebugProblem}, answer material included, and exist for the judge in Phase
 * D. Controllers do not call them. Everything a participant sees is built by
 * {@code ProblemMapper} into DTOs that have no field capable of holding a corrected
 * solution, a bug note, or a hidden test.
 */
@Service
public class ProblemBankService {

    private static final Logger log = LoggerFactory.getLogger(ProblemBankService.class);

    private static final int PROBLEMS_PER_DIFFICULTY = 4;
    private static final int PROBLEMS_PER_LANGUAGE =
            PROBLEMS_PER_DIFFICULTY * Difficulty.values().length;

    /**
     * The bug count each zone is expected to carry.
     *
     * <p>Observed from the bank as authored, not a design principle - and worth
     * stating plainly that EASY and MEDIUM both carry one bug, so bug count is NOT
     * what separates them. Difficulty is carried by the {@code difficulty} field and
     * its points; this map exists only so a bank edited by hand cannot quietly
     * disagree with the shape the rest of the system was calibrated against.
     */
    private static final Map<Difficulty, Integer> EXPECTED_BUG_COUNT = Map.of(
            Difficulty.EASY, 1,
            Difficulty.MEDIUM, 1,
            Difficulty.HARD, 4);

    private final ProblemBankLoader loader;

    /** Language -> the twelve problems, ordered EASY 1-4, MEDIUM 1-4, HARD 1-4. */
    private Map<ArenaLanguage, List<DebugProblem>> byLanguage = Map.of();

    /** Language -> board handle (E-01) -> problem. The lookup every request uses. */
    private Map<ArenaLanguage, Map<String, DebugProblem>> byRef = Map.of();

    public ProblemBankService(ProblemBankLoader loader) {
        this.loader = loader;
    }

    @PostConstruct
    void verify() {
        Map<ArenaLanguage, List<DebugProblem>> loaded = loader.load();
        validate(loaded);

        Map<ArenaLanguage, List<DebugProblem>> ordered = new EnumMap<>(ArenaLanguage.class);
        Map<ArenaLanguage, Map<String, DebugProblem>> refs = new EnumMap<>(ArenaLanguage.class);

        loaded.forEach((language, problems) -> {
            List<DebugProblem> sorted = new ArrayList<>(problems);
            sorted.sort(Comparator
                    .comparing((DebugProblem p) -> p.difficulty().ordinal())
                    .thenComparingInt(DebugProblem::ordinal));
            ordered.put(language, List.copyOf(sorted));

            Map<String, DebugProblem> refIndex = new LinkedHashMap<>();
            sorted.forEach(problem -> refIndex.put(problem.ref(), problem));
            refs.put(language, Map.copyOf(refIndex));
        });

        this.byLanguage = Map.copyOf(ordered);
        this.byRef = Map.copyOf(refs);

        // Counts only. Nothing here prints a statement, a bug note, a test or a
        // solution - a log file is not a place answer material should ever reach.
        log.info("Problem bank loaded: {} languages, {} problems.",
                byLanguage.size(),
                byLanguage.values().stream().mapToInt(List::size).sum());
    }

    // ------------------------------------------------------------- validation

    private void validate(Map<ArenaLanguage, List<DebugProblem>> loaded) {
        List<String> failures = new ArrayList<>();

        for (ArenaLanguage language : ArenaLanguage.values()) {
            List<DebugProblem> problems = loaded.get(language);
            if (problems == null || problems.isEmpty()) {
                failures.add("no bank found for " + language.bankName());
                continue;
            }
            if (problems.size() != PROBLEMS_PER_LANGUAGE) {
                failures.add(language.bankName() + ": expected " + PROBLEMS_PER_LANGUAGE
                        + " problems, found " + problems.size());
            }

            Map<Difficulty, Integer> perDifficulty = new EnumMap<>(Difficulty.class);
            Set<String> refsSeen = new HashSet<>();

            for (DebugProblem problem : problems) {
                perDifficulty.merge(problem.difficulty(), 1, Integer::sum);

                if (problem.language() != language) {
                    failures.add(problem.id() + ": loaded into the " + language.bankName()
                            + " bank but declares " + problem.language().bankName());
                }
                if (problem.points() != problem.difficulty().points()) {
                    failures.add(problem.id() + ": " + problem.difficulty() + " must be worth "
                            + problem.difficulty().points() + " points, found " + problem.points());
                }
                if (problem.bugCount() != problem.bugs().size()) {
                    failures.add(problem.id() + ": bug_count is " + problem.bugCount()
                            + " but the bugs array holds " + problem.bugs().size());
                }
                Integer expectedBugs = EXPECTED_BUG_COUNT.get(problem.difficulty());
                if (expectedBugs != null && problem.bugCount() != expectedBugs) {
                    failures.add(problem.id() + ": " + problem.difficulty()
                            + " problems carry " + expectedBugs + " bug(s), found "
                            + problem.bugCount());
                }
                if (!refsSeen.add(problem.ref())) {
                    failures.add(language.bankName() + ": duplicate board handle "
                            + problem.ref());
                }
            }

            for (Difficulty difficulty : Difficulty.values()) {
                int found = perDifficulty.getOrDefault(difficulty, 0);
                if (found != PROBLEMS_PER_DIFFICULTY) {
                    failures.add(language.bankName() + ": expected " + PROBLEMS_PER_DIFFICULTY
                            + " " + difficulty + " problems, found " + found);
                }
            }
        }

        // Ids must be unique across the WHOLE bank, not merely within a language.
        // They are what Phase E records against a submission, so a collision would
        // make two different problems indistinguishable in the results.
        Set<String> ids = new HashSet<>();
        loaded.values().stream().flatMap(List::stream).forEach(problem -> {
            if (!ids.add(problem.id())) {
                failures.add("duplicate problem id across the bank: " + problem.id());
            }
        });

        if (!failures.isEmpty()) {
            throw new ProblemBankException(
                    "The debugging problem bank is invalid and the arena cannot run:\n  - "
                            + String.join("\n  - ", failures));
        }
    }

    // ---------------------------------------------------------------- lookups

    /**
     * The twelve problems for a language, in board order.
     *
     * <p>Carries answer material. For the judge and for materialising an attempt's
     * board - never for a response.
     */
    public List<DebugProblem> board(ArenaLanguage language) {
        return byLanguage.getOrDefault(language, List.of());
    }

    /**
     * One problem by its board handle, within a language.
     *
     * <p>The language argument is not a convenience: it is the authorisation check.
     * A handle is only meaningful inside a language, so a participant locked to Java
     * asking for {@code E-01} can only ever receive the Java {@code E-01}. There is
     * no lookup by handle alone, which is what makes reaching another language's
     * bank unexpressible rather than merely forbidden.
     */
    public Optional<DebugProblem> find(ArenaLanguage language, String ref) {
        if (ref == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                byRef.getOrDefault(language, Map.of()).get(ref.trim().toUpperCase()));
    }

    public int totalProblems() {
        return byLanguage.values().stream().mapToInt(List::size).sum();
    }
}
