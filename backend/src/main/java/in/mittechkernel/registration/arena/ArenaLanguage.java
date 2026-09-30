package in.mittechkernel.registration.arena;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The five languages a participant may debug in.
 *
 * <p>An enum rather than a table because these are not per-event policy - they are
 * the runtimes the judge has installed and the banks that exist on disk. A sixth
 * language is not a data change; it is a new problem bank, a new Judge0 runtime and
 * a deploy.
 *
 * <p>{@link #bankName} is the join key to the JSON problem banks in
 * {@code resources/debugging/}, whose {@code language} fields read "C", "C++",
 * "Java", "JavaScript" and "Python". Phase C's loader asserts that every enum
 * constant finds a bank and every bank finds a constant, so a rename on either side
 * fails at startup rather than at the first participant.
 *
 * <p>{@link #runtime} is display text for the language cards, and it is a claim
 * about what will actually compile the participant's code. These five were read
 * from the running judge's {@code /languages} endpoint (Judge0 1.13.1, image
 * {@code mrkushalsm/judge0:latest}), not assumed - the placeholders they replaced
 * were wrong in every case.
 *
 * <p>They must be re-read if the judge is ever upgraded. A card promising Java 21
 * in front of a compiler that is actually OpenJDK 13 is a promise the arena cannot
 * keep, and the student only finds out when their code will not build.
 */
public enum ArenaLanguage {

    C("c", "C", "GCC 9.2.0", "C"),
    CPP("cpp", "C++", "GCC 9.2.0", "C++"),
    JAVA("java", "Java", "OpenJDK 13.0.1", "Java"),
    PYTHON("python", "Python", "Python 3.8.1", "Python"),
    JAVASCRIPT("javascript", "JavaScript", "Node.js 12.14.0", "JavaScript");

    private final String id;
    private final String label;
    private final String runtime;
    private final String bankName;

    ArenaLanguage(String id, String label, String runtime, String bankName) {
        this.id = id;
        this.label = label;
        this.runtime = runtime;
        this.bankName = bankName;
    }

    /** Stable wire identifier. Lower case, url-safe, never the display label. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String runtime() {
        return runtime;
    }

    /** The {@code language} value used by the JSON problem banks. */
    public String bankName() {
        return bankName;
    }

    /**
     * Resolve the {@code language} value written inside a JSON problem bank.
     *
     * <p>Matched case-insensitively against {@link #bankName}, which is how a bank
     * file is identified - the filenames are not load-bearing and one bank has no
     * envelope to carry a language in, so the per-problem field is the authority.
     */
    public static Optional<ArenaLanguage> fromBankName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalised = raw.trim();
        return Arrays.stream(values())
                .filter(language -> language.bankName.equalsIgnoreCase(normalised))
                .findFirst();
    }

    /** Every supported identifier, for error details and client validation hints. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(ArenaLanguage::id).toList();
    }

    /**
     * Resolve a wire identifier.
     *
     * <p>Returns empty rather than throwing: the caller decides whether an unknown
     * language is a validation failure or simply an absent filter, and only the
     * caller knows which refusal the participant should see.
     */
    public static Optional<ArenaLanguage> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalised = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language.id.equals(normalised))
                .findFirst();
    }
}
