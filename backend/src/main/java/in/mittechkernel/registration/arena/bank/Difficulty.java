package in.mittechkernel.registration.arena.bank;

import java.util.Locale;
import java.util.Optional;

/**
 * The three zones, and what a problem in each is worth.
 *
 * <p>Points live here rather than being read per-problem at scoring time only
 * because every problem in the bank already agrees with this table - all 20 EASY
 * problems carry 100, all 20 MEDIUM carry 200, all 20 HARD carry 300. The loader
 * asserts that agreement at startup, so the two can never drift apart silently.
 *
 * <p>{@code refPrefix} is the letter used in the URL-safe board handle: E-01, M-03,
 * H-04. Bank ids are not usable in a path - {@code C++-E-001} contains characters
 * some proxies rewrite inside a path segment - so the API addresses problems by
 * this instead.
 */
public enum Difficulty {

    EASY("E", 100),
    MEDIUM("M", 200),
    HARD("H", 300);

    private final String refPrefix;
    private final int points;

    Difficulty(String refPrefix, int points) {
        this.refPrefix = refPrefix;
        this.points = points;
    }

    public String refPrefix() {
        return refPrefix;
    }

    public int points() {
        return points;
    }

    /** The board handle for the nth problem in this zone: {@code E-01}. */
    public String ref(int ordinal) {
        return "%s-%02d".formatted(refPrefix, ordinal);
    }

    public static Optional<Difficulty> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
