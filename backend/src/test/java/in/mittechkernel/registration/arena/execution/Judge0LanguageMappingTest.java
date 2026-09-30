package in.mittechkernel.registration.arena.execution;

import in.mittechkernel.registration.arena.ArenaLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The language mapping, pinned.
 *
 * <p>These ids were read from the running judge's {@code /languages} endpoint rather
 * than assumed, and this test is what stops them drifting. If the judge is upgraded
 * and the ids move, this fails - which is the point. The failure mode it prevents is
 * the quiet one: an id that still exists but now selects a different compiler from
 * the one the 60 problems were authored and validated against.
 */
class Judge0LanguageMappingTest {

    @ParameterizedTest(name = "{0} is mapped")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("every arena language has a judge id")
    void everyLanguageIsMapped(ArenaLanguage language) {
        assertThat(Judge0Languages.idFor(language)).isPositive();
    }

    @Test
    @DisplayName("the mapping is exactly the five verified ids")
    void mappingMatchesTheVerifiedInstance() {
        // Judge0 1.13.1, image mrkushalsm/judge0:latest, isolate 2.2.1.
        assertThat(Judge0Languages.all()).containsExactlyInAnyOrderEntriesOf(Map.of(
                ArenaLanguage.C, 50,
                ArenaLanguage.CPP, 54,
                ArenaLanguage.JAVA, 62,
                ArenaLanguage.JAVASCRIPT, 63,
                ArenaLanguage.PYTHON, 71));
    }

    @Test
    @DisplayName("the mapping covers the enum exactly - no extras, none missing")
    void mappingIsExhaustive() {
        assertThat(Judge0Languages.all()).hasSize(ArenaLanguage.values().length);
        assertThat(Judge0Languages.all().keySet())
                .containsExactlyInAnyOrder(ArenaLanguage.values());
    }

    @Test
    @DisplayName("the runtime labels shown to participants match the mapped compilers")
    void runtimeLabelsMatchTheJudge() {
        // These are displayed on the language cards, so they are a promise about what
        // will compile the code. Read from /languages, not invented - the
        // placeholders they replaced were wrong in all five cases.
        assertThat(ArenaLanguage.C.runtime()).isEqualTo("GCC 9.2.0");
        assertThat(ArenaLanguage.CPP.runtime()).isEqualTo("GCC 9.2.0");
        assertThat(ArenaLanguage.JAVA.runtime()).isEqualTo("OpenJDK 13.0.1");
        assertThat(ArenaLanguage.JAVASCRIPT.runtime()).isEqualTo("Node.js 12.14.0");
        assertThat(ArenaLanguage.PYTHON.runtime()).isEqualTo("Python 3.8.1");
    }

    @Test
    @DisplayName("limits default to the locked architecture's values when unset")
    void limitsHaveArchitectureDefaults() {
        Judge0Properties defaults = new Judge0Properties(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertThat(defaults.baseUrl()).isEqualTo("http://localhost:2358");
        assertThat(defaults.cpuTimeLimitSeconds()).isEqualTo(2.0);
        assertThat(defaults.memoryLimitKb()).isEqualTo(256 * 1024);
        assertThat(defaults.maxOutputChars()).isEqualTo(64 * 1024);
    }

    @Test
    @DisplayName("the polling budget outlasts the sandbox's own wall limit")
    void pollBudgetOutlastsWallLimit() {
        Judge0Properties defaults = new Judge0Properties(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        // The budget - not the HTTP read timeout - is what waits for a verdict now.
        // If it were shorter than the sandbox's wall limit, a slow-but-valid
        // submission would be abandoned and reported as an outage.
        assertThat(defaults.pollBudgetMs())
                .isGreaterThan((int) (defaults.wallTimeLimitSeconds() * 1000));

        // And the ramp has to be a ramp.
        assertThat(defaults.pollIntervalMs()).isPositive();
        assertThat(defaults.maxPollIntervalMs())
                .isGreaterThanOrEqualTo(defaults.pollIntervalMs());
    }

    @Test
    @DisplayName("a max interval below the starting interval is corrected, not honoured")
    void invertedIntervalsAreCorrected() {
        Judge0Properties odd = new Judge0Properties(null, null, 0, 0, 0, 0, 0, 0, 900, 100, 0);

        // Otherwise the ramp would shrink on every poll.
        assertThat(odd.maxPollIntervalMs()).isEqualTo(900);
    }

    @Test
    @DisplayName("a trailing slash on the base URL is normalised away")
    void baseUrlIsNormalised() {
        assertThat(new Judge0Properties("http://judge:2358///", null,
                0, 0, 0, 0, 0, 0, 0, 0, 0).baseUrl())
                .isEqualTo("http://judge:2358");
    }
}
