package in.mittechkernel.registration.arena.execution;

import in.mittechkernel.registration.arena.ArenaLanguage;

import java.util.EnumMap;
import java.util.Map;

/**
 * The five arena languages, mapped to this Judge0 deployment's language ids.
 *
 * <h2>Why these numbers and not others</h2>
 *
 * <p><b>Judge0 language ids are deployment-specific.</b> They are not stable across
 * versions: C++ has shipped as 54, 76 and 105 in different releases, and Java as 62
 * and 91. A mapping guessed from memory has two failure modes, and the second is far
 * worse than the first - either the id does not exist and every run fails loudly, or
 * it exists and silently selects a different compiler from the one the problem bank
 * was authored against.
 *
 * <p>These five were therefore read from the running instance's {@code /languages}
 * endpoint, not assumed:
 *
 * <pre>
 *   Judge0 1.13.1  (image mrkushalsm/judge0:latest, isolate 2.2.1)
 *     50  C (GCC 9.2.0)
 *     54  C++ (GCC 9.2.0)
 *     62  Java (OpenJDK 13.0.1)
 *     63  JavaScript (Node.js 12.14.0)
 *     71  Python (3.8.1)
 * </pre>
 *
 * <p>If the judge is ever upgraded or its image swapped, this table must be
 * re-read from {@code /languages} before the event. It is a small file to change
 * and an unpleasant thing to get wrong.
 *
 * <h2>Client input is never consulted</h2>
 *
 * <p>The only way into this map is an {@link ArenaLanguage}, which is only ever
 * obtained from the attempt row. There is no method here taking a string, an int, or
 * anything a request could carry - so a participant cannot name a Judge0 id, and
 * cannot reach a runtime their attempt is not locked to.
 */
final class Judge0Languages {

    private static final Map<ArenaLanguage, Integer> IDS = new EnumMap<>(ArenaLanguage.class);

    static {
        IDS.put(ArenaLanguage.C, 50);
        IDS.put(ArenaLanguage.CPP, 54);
        IDS.put(ArenaLanguage.JAVA, 62);
        IDS.put(ArenaLanguage.JAVASCRIPT, 63);
        IDS.put(ArenaLanguage.PYTHON, 71);
    }

    private Judge0Languages() {
    }

    /**
     * The judge's id for an arena language.
     *
     * <p>A missing entry is a programming error, not a runtime condition - the map
     * is exhaustive over the enum and a test asserts it stays that way.
     */
    static int idFor(ArenaLanguage language) {
        Integer id = IDS.get(language);
        if (id == null) {
            throw new ExecutionUnavailableException(
                    "No judge language is configured for " + language.id() + ".");
        }
        return id;
    }

    /** Exposed for the test that proves every language is mapped. */
    static Map<ArenaLanguage, Integer> all() {
        return Map.copyOf(IDS);
    }
}
