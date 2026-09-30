package in.mittechkernel.registration.arena.execution;

/**
 * Decides whether a program's output matches what a test expected.
 *
 * <h2>The rules, exactly and exhaustively</h2>
 *
 * <p>Applied to both sides before comparison:
 *
 * <ol>
 *   <li><b>Line endings normalised.</b> {@code \r\n} and bare {@code \r} both become
 *       {@code \n}. A participant on Windows must not fail for their platform.</li>
 *   <li><b>Trailing whitespace removed from every line.</b> A stray space after the
 *       last number on a line is invisible in a terminal and irrelevant to whether
 *       the bug was found.</li>
 *   <li><b>Trailing blank lines removed from the end.</b> {@code println} on the
 *       final line is the normal way to write these programs, and the bank's
 *       expected outputs are not consistent about whether they include it.</li>
 * </ol>
 *
 * <p>And nothing else. In particular this comparison is:
 *
 * <ul>
 *   <li><b>Case-sensitive.</b> {@code YES} is not {@code yes}.</li>
 *   <li><b>Whitespace-sensitive within a line.</b> {@code 1 2 3} is not
 *       {@code 1  2  3}, and leading indentation is significant.</li>
 *   <li><b>Not numeric.</b> {@code 1.0} is not {@code 1}, and there is no epsilon.
 *       No problem in the bank requires float tolerance; adding one silently would
 *       mean some wrong answers quietly passing.</li>
 *   <li><b>Not order-insensitive.</b> Lines must appear in the expected order.</li>
 * </ul>
 *
 * <p>Every one of those omissions is deliberate. A comparator that is more generous
 * than it is documented to be is a comparator nobody can predict, and the first time
 * it matters is when a student disputes a result.
 *
 * <p>Comparison is server-side and nothing else. The frontend is shown whether a
 * case passed; it never decides it.
 */
final class OutputComparator {

    private OutputComparator() {
    }

    static boolean matches(String expected, String actual) {
        return normalise(expected).equals(normalise(actual));
    }

    /** Applies rules 1-3. Null is treated as empty so a crash compares cleanly. */
    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }

        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        StringBuilder out = new StringBuilder();
        int lastMeaningful = -1;

        // Strip trailing whitespace per line first, so a line of only spaces counts
        // as blank when deciding where the output really ends.
        for (int i = 0; i < lines.length; i++) {
            lines[i] = stripTrailing(lines[i]);
            if (!lines[i].isEmpty()) {
                lastMeaningful = i;
            }
        }

        for (int i = 0; i <= lastMeaningful; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i]);
        }
        return out.toString();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
