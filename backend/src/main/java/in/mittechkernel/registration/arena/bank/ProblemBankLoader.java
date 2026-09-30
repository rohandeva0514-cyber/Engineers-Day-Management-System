package in.mittechkernel.registration.arena.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.mittechkernel.registration.arena.ArenaLanguage;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the five JSON problem banks off the classpath.
 *
 * <h2>Files are found, not named</h2>
 *
 * <p>The loader globs {@code classpath:debugging/*.json} and decides which language
 * each file holds by reading the {@code language} field <em>inside</em> it. Nothing
 * here depends on a filename. That matters because the files are named
 * {@code Engineers_Day_2026_CPP_Debugging_Arena_Problem_Bank.json} and friends, and
 * because one of them has no envelope at all to put a language in.
 *
 * <h2>Three shapes, one model</h2>
 *
 * <p>The banks were authored separately and are not uniform. All three variations
 * below are real, present today, and handled here rather than by editing the files:
 *
 * <ol>
 *   <li><b>Envelope.</b> Four banks are objects with a {@code problems} array. The C
 *       bank is a bare array with no envelope - so it also has no envelope-level
 *       {@code language}, which is the second reason the per-problem field is the
 *       authority.</li>
 *   <li><b>Test-case key.</b> Most tests spell the expected value
 *       {@code expected_output}; 34 visible cases in the C++ bank spell it
 *       {@code output}.</li>
 *   <li><b>Test-case form.</b> The C bank writes every one of its 84 cases as a
 *       two-element array {@code ["input", "expected"]} rather than an object.</li>
 * </ol>
 *
 * <p>Reading them into one {@link TestCase} here means nothing downstream - the
 * judge, the DTOs, the tests - ever learns that the variation existed.
 *
 * <h2>Hand-built from JsonNode, deliberately</h2>
 *
 * <p>Rather than annotating the records and letting Jackson bind them. Three
 * competing shapes would need custom deserialisers anyway, and a loader whose whole
 * job is to fail loudly on malformed input is clearer when the field reads are
 * explicit and each one can name what it could not find.
 */
@Component
public class ProblemBankLoader {

    private static final String BANK_LOCATION = "classpath:debugging/*.json";

    private final ObjectMapper objectMapper;

    public ProblemBankLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Load every bank on the classpath, grouped by language.
     *
     * <p>Throws on anything it cannot make sense of. A malformed bank must stop the
     * application from starting, not produce a half-populated arena that hands some
     * participants a short board on the day.
     */
    public Map<ArenaLanguage, List<DebugProblem>> load() {
        Resource[] resources = findBankFiles();

        Map<ArenaLanguage, List<DebugProblem>> byLanguage = new EnumMap<>(ArenaLanguage.class);

        for (Resource resource : resources) {
            String filename = resource.getFilename() == null ? "<unnamed>" : resource.getFilename();
            for (DebugProblem problem : readBank(resource, filename)) {
                byLanguage.computeIfAbsent(problem.language(), key -> new ArrayList<>())
                        .add(problem);
            }
        }
        return byLanguage;
    }

    private Resource[] findBankFiles() {
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver().getResources(BANK_LOCATION);
            if (resources.length == 0) {
                throw new ProblemBankException(
                        "No problem banks found at " + BANK_LOCATION
                                + ". The arena cannot run without them.");
            }
            return resources;
        } catch (IOException cause) {
            throw new ProblemBankException("Could not scan " + BANK_LOCATION, cause);
        }
    }

    private List<DebugProblem> readBank(Resource resource, String filename) {
        JsonNode root;
        try (InputStream stream = resource.getInputStream()) {
            root = objectMapper.readTree(stream);
        } catch (IOException cause) {
            throw new ProblemBankException(filename + " could not be parsed as JSON", cause);
        }

        // Variation 1: bare array (the C bank) or an envelope with `problems`.
        JsonNode problems = root.isArray() ? root : root.path("problems");
        if (!problems.isArray() || problems.isEmpty()) {
            throw new ProblemBankException(
                    filename + " has no problems array. Expected either a top-level array "
                            + "or an object with a non-empty \"problems\" field.");
        }

        // Ordinals restart per difficulty within each file, which is what makes the
        // board handle (E-01 .. H-04) stable and independent of file order changes
        // within a zone.
        Map<Difficulty, Integer> nextOrdinal = new EnumMap<>(Difficulty.class);

        List<DebugProblem> loaded = new ArrayList<>();
        for (JsonNode node : problems) {
            loaded.add(readProblem(node, filename, nextOrdinal));
        }
        return loaded;
    }

    private DebugProblem readProblem(JsonNode node, String filename,
                                     Map<Difficulty, Integer> nextOrdinal) {
        String id = text(node, "id", filename, "<unknown>");

        String rawLanguage = text(node, "language", filename, id);
        ArenaLanguage language = ArenaLanguage.fromBankName(rawLanguage)
                .orElseThrow(() -> new ProblemBankException(
                        filename + ": problem " + id + " declares language '" + rawLanguage
                                + "', which is not one of the five supported languages."));

        String rawDifficulty = text(node, "difficulty", filename, id);
        Difficulty difficulty = Difficulty.parse(rawDifficulty)
                .orElseThrow(() -> new ProblemBankException(
                        filename + ": problem " + id + " has difficulty '" + rawDifficulty
                                + "'. Expected EASY, MEDIUM or HARD."));

        int ordinal = nextOrdinal.merge(difficulty, 1, Integer::sum);

        return new DebugProblem(
                id,
                difficulty.ref(ordinal),
                ordinal,
                text(node, "title", filename, id),
                language,
                difficulty,
                integer(node, "points", filename, id),
                integer(node, "bug_count", filename, id),
                strings(node.path("concepts")),
                text(node, "problem_statement", filename, id),
                text(node, "input_format", filename, id),
                text(node, "output_format", filename, id),
                text(node, "constraints", filename, id),
                text(node, "buggy_code", filename, id),
                readBugs(node.path("bugs"), filename, id),
                text(node, "corrected_code", filename, id),
                readTests(node.path("visible_tests"), filename, id, "visible_tests"),
                readTests(node.path("hidden_tests"), filename, id, "hidden_tests"),
                node.path("estimated_time_minutes").asInt(0));
    }

    private List<BugNote> readBugs(JsonNode array, String filename, String id) {
        if (!array.isArray() || array.isEmpty()) {
            throw new ProblemBankException(filename + ": problem " + id + " has no bugs array.");
        }
        List<BugNote> bugs = new ArrayList<>();
        for (JsonNode bug : array) {
            bugs.add(new BugNote(
                    text(bug, "location", filename, id),
                    text(bug, "description", filename, id),
                    text(bug, "why_wrong", filename, id)));
        }
        return bugs;
    }

    private List<TestCase> readTests(JsonNode array, String filename, String id, String field) {
        if (!array.isArray() || array.isEmpty()) {
            throw new ProblemBankException(
                    filename + ": problem " + id + " has no " + field + ".");
        }
        List<TestCase> tests = new ArrayList<>();
        for (JsonNode test : array) {
            tests.add(readTest(test, filename, id, field));
        }
        return tests;
    }

    /**
     * One test case, in whichever of the three forms the bank happens to use.
     *
     * <p>Variations 2 and 3 from the class note collapse here. Anything that is
     * neither a two-element array nor an object carrying an input and one of the two
     * expected-value spellings is a hard failure - guessing at a fourth shape would
     * mean guessing at what a participant is judged against.
     */
    private TestCase readTest(JsonNode test, String filename, String id, String field) {
        if (test.isArray()) {
            if (test.size() != 2) {
                throw new ProblemBankException(
                        filename + ": problem " + id + " has an array-form " + field
                                + " with " + test.size() + " elements. Expected exactly 2.");
            }
            return new TestCase(test.get(0).asText(), test.get(1).asText());
        }

        if (test.isObject()) {
            JsonNode input = test.get("input");
            JsonNode expected = test.has("expected_output")
                    ? test.get("expected_output")
                    : test.get("output");

            if (input == null || expected == null) {
                throw new ProblemBankException(
                        filename + ": problem " + id + " has a " + field + " entry missing "
                                + "\"input\" or \"expected_output\"/\"output\".");
            }
            return new TestCase(input.asText(), expected.asText());
        }

        throw new ProblemBankException(
                filename + ": problem " + id + " has a " + field
                        + " entry that is neither an object nor a two-element array.");
    }

    // --------------------------------------------------------------- primitives

    private static String text(JsonNode node, String field, String filename, String id) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ProblemBankException(
                    filename + ": problem " + id + " is missing required field \"" + field + "\".");
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String field, String filename, String id) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new ProblemBankException(
                    filename + ": problem " + id + " is missing numeric field \"" + field + "\".");
        }
        return value.asInt();
    }

    private static List<String> strings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** Unchecked: every one of these is fatal at startup and unrecoverable at runtime. */
    public static class ProblemBankException extends RuntimeException {
        public ProblemBankException(String message) {
            super(message);
        }

        public ProblemBankException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Load specific resources rather than scanning the classpath.
     *
     * <p>Exists so tests can feed the loader a deliberately broken bank and assert
     * that it refuses it. The refusal behaviour is the reason this class is worth
     * testing at all - a loader that quietly tolerates a malformed problem is how a
     * short board reaches an event - so that path needs to be reachable from a test.
     */
    public Map<ArenaLanguage, List<DebugProblem>> loadFrom(Resource[] resources) {
        Map<ArenaLanguage, List<DebugProblem>> byLanguage = new HashMap<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename() == null ? "<unnamed>" : resource.getFilename();
            for (DebugProblem problem : readBank(resource, filename)) {
                byLanguage.computeIfAbsent(problem.language(), key -> new ArrayList<>())
                        .add(problem);
            }
        }
        return byLanguage;
    }
}
