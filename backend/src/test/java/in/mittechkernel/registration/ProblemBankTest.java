package in.mittechkernel.registration;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.DebugProblem;
import in.mittechkernel.registration.arena.bank.Difficulty;
import in.mittechkernel.registration.arena.bank.ProblemBankLoader;
import in.mittechkernel.registration.arena.bank.ProblemBankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The problem bank, as actually shipped.
 *
 * <p>These assert the five JSON resources directly. The bank is data, so a mistake
 * in it is a product bug that no amount of correct Java would catch - a bank short
 * one HARD problem would give every participant in that language an eleven-problem
 * board on the day, and every other test would still pass.
 *
 * <p>Nothing here prints a statement, a solution, a bug note or a test case. A CI
 * log is not a place answer material should reach either.
 */
class ProblemBankTest extends PostgresIntegrationTest {

    @Autowired
    private ProblemBankService bank;

    @Test
    @DisplayName("all five language banks load")
    void allBanksLoad() {
        for (ArenaLanguage language : ArenaLanguage.values()) {
            assertThat(bank.board(language))
                    .as("bank for %s", language.bankName())
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("the bank holds exactly 60 problems")
    void bankHoldsSixtyProblems() {
        assertThat(bank.totalProblems()).isEqualTo(60);
    }

    @ParameterizedTest(name = "{0} has 12 problems, 4 per zone")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("every language has 12 problems split 4/4/4")
    void everyLanguageHasTwelveProblems(ArenaLanguage language) {
        List<DebugProblem> problems = bank.board(language);

        assertThat(problems).hasSize(12);
        for (Difficulty difficulty : Difficulty.values()) {
            assertThat(problems)
                    .filteredOn(problem -> problem.difficulty() == difficulty)
                    .as("%s problems in %s", difficulty, language.bankName())
                    .hasSize(4);
        }
    }

    @ParameterizedTest(name = "{0}: points match difficulty")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("points are 100 / 200 / 300 by difficulty")
    void pointsMatchDifficulty(ArenaLanguage language) {
        for (DebugProblem problem : bank.board(language)) {
            assertThat(problem.points())
                    .as("%s (%s)", problem.id(), problem.difficulty())
                    .isEqualTo(problem.difficulty().points());
        }
    }

    @Test
    @DisplayName("the maximum achievable score is 2400")
    void maximumScoreIsTwentyFourHundred() {
        for (ArenaLanguage language : ArenaLanguage.values()) {
            int total = bank.board(language).stream().mapToInt(DebugProblem::points).sum();
            assertThat(total)
                    .as("total points available in %s", language.bankName())
                    .isEqualTo(2400);
        }
    }

    @Test
    @DisplayName("every problem id is unique across the whole bank")
    void idsAreUniqueAcrossTheBank() {
        Set<String> ids = new HashSet<>();
        for (ArenaLanguage language : ArenaLanguage.values()) {
            for (DebugProblem problem : bank.board(language)) {
                assertThat(ids.add(problem.id()))
                        .as("duplicate problem id %s", problem.id())
                        .isTrue();
            }
        }
        assertThat(ids).hasSize(60);
    }

    @ParameterizedTest(name = "{0}: problems declare their own bank's language")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("a problem never lands in the wrong language's bank")
    void problemLanguageMatchesItsBank(ArenaLanguage language) {
        assertThat(bank.board(language))
                .allSatisfy(problem -> assertThat(problem.language()).isEqualTo(language));
    }

    @ParameterizedTest(name = "{0}: board handles are E-01..H-04")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("board handles are stable, unique and URL-safe")
    void boardHandlesAreStable(ArenaLanguage language) {
        List<String> refs = bank.board(language).stream().map(DebugProblem::ref).toList();

        assertThat(refs).containsExactly(
                "E-01", "E-02", "E-03", "E-04",
                "M-01", "M-02", "M-03", "M-04",
                "H-01", "H-02", "H-03", "H-04");

        // Bank ids carry characters that do not belong in a path - C++-E-001 has
        // two. The handle is what the API addresses, so it must never need escaping.
        assertThat(refs).allSatisfy(ref -> assertThat(ref).matches("[EMH]-\\d{2}"));
    }

    @ParameterizedTest(name = "{0}: bug_count agrees with the bugs array")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("declared bug count matches the bugs actually listed")
    void bugCountMatchesBugs(ArenaLanguage language) {
        for (DebugProblem problem : bank.board(language)) {
            assertThat(problem.bugs())
                    .as("bugs listed for %s", problem.id())
                    .hasSize(problem.bugCount());
        }
    }

    @ParameterizedTest(name = "{0}: every problem is complete")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("required participant-facing content is present on every problem")
    void everyProblemIsComplete(ArenaLanguage language) {
        for (DebugProblem problem : bank.board(language)) {
            assertThat(problem.title()).as("%s title", problem.id()).isNotBlank();
            assertThat(problem.problemStatement()).as("%s statement", problem.id()).isNotBlank();
            assertThat(problem.inputFormat()).as("%s input", problem.id()).isNotBlank();
            assertThat(problem.outputFormat()).as("%s output", problem.id()).isNotBlank();
            assertThat(problem.constraints()).as("%s constraints", problem.id()).isNotBlank();
            assertThat(problem.buggyCode()).as("%s buggy code", problem.id()).isNotBlank();
            assertThat(problem.visibleTests()).as("%s visible tests", problem.id()).isNotEmpty();
        }
    }

    @ParameterizedTest(name = "{0}: server-side answer material is present")
    @EnumSource(ArenaLanguage.class)
    @DisplayName("the judge has what it will need, even though participants never see it")
    void answerMaterialIsLoadedForTheJudge(ArenaLanguage language) {
        for (DebugProblem problem : bank.board(language)) {
            assertThat(problem.correctedCode()).as("%s solution", problem.id()).isNotBlank();
            assertThat(problem.hiddenTests()).as("%s hidden tests", problem.id()).isNotEmpty();
            assertThat(problem.bugs()).as("%s bug notes", problem.id()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("the three test-case shapes in the banks all normalise")
    void everyTestCaseNormalises() {
        // The banks are not uniform: most tests are objects using expected_output,
        // 34 C++ visible cases use output, and all 84 C cases are two-element
        // arrays. All three must arrive as the same record with both sides filled.
        for (ArenaLanguage language : ArenaLanguage.values()) {
            for (DebugProblem problem : bank.board(language)) {
                assertThat(problem.visibleTests()).allSatisfy(test -> {
                    assertThat(test.input()).as("%s visible input", problem.id()).isNotNull();
                    assertThat(test.expectedOutput())
                            .as("%s visible expected output", problem.id()).isNotNull();
                });
                assertThat(problem.hiddenTests()).allSatisfy(test -> {
                    assertThat(test.input()).as("%s hidden input", problem.id()).isNotNull();
                    assertThat(test.expectedOutput())
                            .as("%s hidden expected output", problem.id()).isNotNull();
                });
            }
        }
    }

    @Test
    @DisplayName("a problem can only be found inside its own language")
    void lookupIsScopedToLanguage() {
        // E-01 exists in all five banks. Asking for it as a Java participant must
        // return the Java one and nothing else - the handle alone names nothing.
        DebugProblem java = bank.find(ArenaLanguage.JAVA, "E-01").orElseThrow();
        DebugProblem python = bank.find(ArenaLanguage.PYTHON, "E-01").orElseThrow();

        assertThat(java.language()).isEqualTo(ArenaLanguage.JAVA);
        assertThat(python.language()).isEqualTo(ArenaLanguage.PYTHON);
        assertThat(java.id()).isNotEqualTo(python.id());
    }

    @Test
    @DisplayName("an unknown handle resolves to nothing")
    void unknownHandleIsEmpty() {
        assertThat(bank.find(ArenaLanguage.JAVA, "E-99")).isEmpty();
        assertThat(bank.find(ArenaLanguage.JAVA, "nonsense")).isEmpty();
        assertThat(bank.find(ArenaLanguage.JAVA, null)).isEmpty();
    }

    @Test
    @DisplayName("a structurally invalid bank is refused rather than half-loaded")
    void invalidBankIsRejected() {
        // The loader's contract is to fail loudly. A bank missing a required field
        // must stop the application, not produce a problem with a blank statement.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            ProblemBankLoader loader = new ProblemBankLoader(
                    new com.fasterxml.jackson.databind.ObjectMapper());
            loader.loadFrom(new org.springframework.core.io.Resource[]{
                    new org.springframework.core.io.ByteArrayResource(
                            "[{\"id\":\"X-E-001\",\"language\":\"Java\"}]".getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8)) {
                        @Override
                        public String getFilename() {
                            return "broken.json";
                        }
                    }});
        }).isInstanceOf(ProblemBankLoader.ProblemBankException.class)
                .hasMessageContaining("broken.json");
    }

    @Test
    @DisplayName("a bank declaring an unsupported language is refused")
    void unknownLanguageIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            ProblemBankLoader loader = new ProblemBankLoader(
                    new com.fasterxml.jackson.databind.ObjectMapper());
            loader.loadFrom(new org.springframework.core.io.Resource[]{
                    new org.springframework.core.io.ByteArrayResource(
                            "[{\"id\":\"R-E-001\",\"language\":\"Rust\",\"difficulty\":\"EASY\"}]"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                        @Override
                        public String getFilename() {
                            return "rust.json";
                        }
                    }});
        }).isInstanceOf(ProblemBankLoader.ProblemBankException.class)
                .hasMessageContaining("Rust");
    }
}
