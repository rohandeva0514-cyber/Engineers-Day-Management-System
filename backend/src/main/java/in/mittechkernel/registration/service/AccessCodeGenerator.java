package in.mittechkernel.registration.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates event-day access codes.
 *
 * <h2>The alphabet</h2>
 *
 * <p>{@code 23456789ABCDEFGHJKLMNPQRSTUVWXYZ} - 32 characters, with 0, 1, I and O
 * removed. Each of those is half of a pair someone will misread off a phone
 * screen and mistype at a desk under time pressure, and a code that cannot be
 * transcribed reliably is worse than no code at all.
 *
 * <p>32 characters is also exactly 5 bits each, so an 8-character code carries a
 * clean 40 bits of entropy - about 1.1 trillion possibilities. Guessing one by
 * chance is not a practical attack, and the codes in use at any time are a
 * vanishing fraction of that space.
 *
 * <h2>SecureRandom, not Random</h2>
 *
 * <p>{@link java.util.Random} is a linear congruential generator: observing a
 * couple of outputs lets you reconstruct its internal state and predict every
 * code it will ever produce. That is fatal for a value that admits someone to a
 * terminal. {@link SecureRandom} is seeded from the OS entropy pool and has no
 * such property.
 *
 * <h2>Uniqueness</h2>
 *
 * <p>Not enforced here. The generator proposes; the unique index on
 * {@code registration.access_code} disposes, and the caller retries on collision.
 * That is the only approach that is correct under concurrency - a "SELECT, then
 * INSERT if absent" check has a window between the two statements where a second
 * request can take the same value.
 */
@Component
public class AccessCodeGenerator {

    /** Unambiguous when read aloud or typed: no 0/O, no 1/I/L. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            // nextInt(bound) is uniform over the range; a modulus of a raw int
            // would skew the distribution towards the start of the alphabet.
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }

    /** Codes are stored and compared upper-cased, so a student may type either. */
    public static String normalise(String code) {
        return code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
