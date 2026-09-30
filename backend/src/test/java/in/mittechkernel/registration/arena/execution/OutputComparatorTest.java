package in.mittechkernel.registration.arena.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The output comparison rules, asserted one at a time.
 *
 * <p>In the comparator's own package so it can call the real thing directly. The
 * alternative - widening {@code OutputComparator} to public, or reimplementing its
 * normalisation in the test - would either loosen production visibility for a test's
 * convenience or assert a copy of the logic rather than the logic.
 *
 * <p>The cases that matter most are the ones asserting what is NOT forgiven. A
 * comparator quietly more generous than its documentation is one nobody can predict,
 * and the first time that matters is a disputed result at a prize table.
 */
class OutputComparatorTest {

    // ------------------------------------------------------------ what passes

    @ParameterizedTest(name = "expected [{0}] vs actual [{1}] matches")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "15|15",
            "15\\n|15",
            "15|15\\n",
            "15\\n\\n\\n|15",
            "15 |15",
            "1 2 3|1 2 3   ",
            "a\\nb|a\\nb\\n",
            "a\\nb\\n|a  \\nb\\t",
            "|",
            "|\\n\\n"
    })
    @DisplayName("line endings, trailing whitespace and trailing blank lines are forgiven")
    void documentedTolerances(String expected, String actual) {
        assertThat(OutputComparator.matches(unescape(expected), unescape(actual))).isTrue();
    }

    @Test
    @DisplayName("Windows and old-Mac line endings match Unix ones")
    void lineEndingsAreNormalised() {
        assertThat(OutputComparator.matches("a\nb\nc", "a\r\nb\r\nc")).isTrue();
        assertThat(OutputComparator.matches("a\nb", "a\rb")).isTrue();
    }

    @Test
    @DisplayName("a line of only whitespace at the end counts as blank")
    void trailingWhitespaceOnlyLinesAreTrimmed() {
        assertThat(OutputComparator.matches("15", "15\n   \n\t\n")).isTrue();
    }

    // ----------------------------------------------------------- what does not

    // ignoreLeadingAndTrailingWhitespace=false is load-bearing here: JUnit trims
    // CsvSource values by default, which silently turned the " abc" case into "abc"
    // and made it compare a string with itself.
    @ParameterizedTest(name = "expected [{0}] vs actual [{1}] does NOT match")
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
            "YES|yes",
            "1 2 3|1  2  3",
            "15|15.0",
            "1|1.00",
            "a\\nb|b\\na",
            "abc| abc",
            "15|16",
            "10|1 0"
    })
    @DisplayName("case, inner whitespace, numeric form, order and indentation are significant")
    void documentedStrictness(String expected, String actual) {
        assertThat(OutputComparator.matches(unescape(expected), unescape(actual))).isFalse();
    }

    @Test
    @DisplayName("blank lines in the middle are significant; only trailing ones are trimmed")
    void interiorBlankLinesMatter() {
        assertThat(OutputComparator.matches("a\n\nb", "a\nb")).isFalse();
        assertThat(OutputComparator.matches("a\n\nb", "a\n\nb\n\n")).isTrue();
    }

    @Test
    @DisplayName("there is no float tolerance, deliberately")
    void thereIsNoEpsilon() {
        // No problem in the bank needs one. Adding it silently would mean some wrong
        // answers quietly passing, which is worse than a student having to print the
        // format the problem asked for.
        assertThat(OutputComparator.matches("0.1", "0.10")).isFalse();
        assertThat(OutputComparator.matches("3.14", "3.140000")).isFalse();
    }

    @Test
    @DisplayName("a crashed program that printed nothing fails cleanly")
    void emptyOutputFailsWithoutThrowing() {
        assertThat(OutputComparator.matches("15", null)).isFalse();
        assertThat(OutputComparator.matches("15", "")).isFalse();
        assertThat(OutputComparator.matches(null, "15")).isFalse();
        assertThat(OutputComparator.matches(null, null)).isTrue();
    }

    @Test
    @DisplayName("normalisation is idempotent")
    void normaliseIsStable() {
        String once = OutputComparator.normalise("a \r\n b  \n\n");
        assertThat(OutputComparator.normalise(once)).isEqualTo(once);
    }

    private static String unescape(String value) {
        return value == null ? "" : value.replace("\\n", "\n").replace("\\t", "\t");
    }
}
