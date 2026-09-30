package in.mittechkernel.registration.arena.execution;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.TestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real judge, really compiling and running code.
 *
 * <p><b>Skips itself when Judge0 is not reachable</b> rather than failing. A suite
 * that cannot run without a container is a suite people stop running, and the other
 * ~250 tests have no business depending on an external service. When the judge is up
 * these run; when it is not, they report as skipped and the rest of the suite is
 * unaffected.
 *
 * <p>Talks to the engine directly, with no HTTP layer, session or attempt involved.
 * Everything around execution is covered deterministically by
 * {@code ArenaExecutionApiTest} against a fake; what is left for here is the part
 * only a real compiler can answer.
 */
class Judge0IntegrationTest {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "JUDGE0_BASE_URL", "http://localhost:2358");

    private static ExecutionEngine engine;

    @BeforeAll
    static void requireJudge() {
        Assumptions.assumeTrue(reachable(),
                "Judge0 is not reachable at " + BASE_URL + " - skipping live execution tests");

        // Zeros take the documented defaults, so this also exercises the fallback
        // path rather than pinning values the production config might move.
        engine = new Judge0ExecutionEngine(
                new RestTemplateBuilder(),
                new Judge0Properties(BASE_URL, null, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static boolean reachable() {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) URI.create(BASE_URL + "/languages").toURL()
                            .openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            boolean ok = connection.getResponseCode() == 200;
            connection.disconnect();
            return ok;
        } catch (IOException | RuntimeException unreachable) {
            return false;
        }
    }

    private static TestCase test(String input, String expected) {
        return new TestCase(input, expected);
    }

    // ------------------------------------------------------------ every language

    @Test
    @DisplayName("all five languages compile and run correct code")
    void everyLanguageRuns() {
        record Case(ArenaLanguage language, String source) {
        }

        // Each reads one integer and prints it doubled.
        List<Case> cases = List.of(
                new Case(ArenaLanguage.C,
                        "#include <stdio.h>\nint main(){int n;scanf(\"%d\",&n);printf(\"%d\\n\",n*2);return 0;}"),
                new Case(ArenaLanguage.CPP,
                        "#include <iostream>\nint main(){int n;std::cin>>n;std::cout<<n*2<<std::endl;return 0;}"),
                new Case(ArenaLanguage.JAVA,
                        "import java.util.*;public class Main{public static void main(String[] a){"
                                + "Scanner s=new Scanner(System.in);System.out.println(s.nextInt()*2);}}"),
                new Case(ArenaLanguage.PYTHON, "print(int(input())*2)"),
                new Case(ArenaLanguage.JAVASCRIPT,
                        "const d=require('fs').readFileSync(0,'utf8').trim();console.log(Number(d)*2);"));

        for (Case c : cases) {
            ExecutionReport report = engine.execute(
                    c.language(), c.source(), List.of(test("21", "42")));

            assertThat(report.status())
                    .as("%s should compile and run: %s", c.language().id(), report.compileOutput())
                    .isEqualTo(ExecutionStatus.ACCEPTED);
            assertThat(report.allPassed()).as("%s verdict", c.language().id()).isTrue();
        }
    }

    // ------------------------------------------------------------------ verdicts

    @Test
    @DisplayName("correct output across several cases is ACCEPTED")
    void multipleCasesAllPass() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "print(int(input())*2)",
                List.of(test("1", "2"), test("5", "10"), test("50", "100")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.ACCEPTED);
        assertThat(report.passedCount()).isEqualTo(3);
        assertThat(report.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("wrong output is WRONG_ANSWER, not an error")
    void wrongOutputIsWrongAnswer() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "print(int(input())*3)", List.of(test("2", "4")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.WRONG_ANSWER);
        assertThat(report.allPassed()).isFalse();
        assertThat(report.cases().get(0).actualOutput().trim()).isEqualTo("6");
    }

    @Test
    @DisplayName("one failing case among several is still WRONG_ANSWER")
    void partialPassIsWrongAnswer() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "n=int(input())\nprint(2 if n==1 else 999)",
                List.of(test("1", "2"), test("5", "10")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.WRONG_ANSWER);
        assertThat(report.passedCount()).isEqualTo(1);
        assertThat(report.allPassed()).isFalse();
    }

    @Test
    @DisplayName("a syntax error is COMPILATION_ERROR and carries diagnostics")
    void compilationErrorIsReported() {
        ExecutionReport report = engine.execute(ArenaLanguage.CPP,
                "int main(){ this is not c++ }", List.of(test("1", "1")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(report.compileOutput()).isNotBlank();

        // No per-case detail: the build failed, so nothing ran.
        assertThat(report.cases()).isEmpty();
    }

    @Test
    @DisplayName("a Python syntax error is a compilation error too")
    void interpretedSyntaxErrorIsCompilationError() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "def broken(\n", List.of(test("1", "1")));

        assertThat(report.status())
                .isIn(ExecutionStatus.COMPILATION_ERROR, ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("a crash is RUNTIME_ERROR")
    void runtimeErrorIsReported() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "raise SystemExit(1)", List.of(test("1", "1")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(report.allPassed()).isFalse();
    }

    @Test
    @DisplayName("an uncaught exception surfaces its stderr")
    void runtimeErrorCarriesStderr() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "print(1/0)", List.of(test("1", "1")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(report.cases().get(0).stderr()).contains("ZeroDivisionError");
    }

    /**
     * An infinite loop is a real verdict, promptly.
     *
     * <p>This is the test the transport fix exists for. Under {@code wait=true} the
     * call never returned and the participant was told the execution service was
     * unavailable - a routine mistake in a debugging contest reported as an outage.
     * Isolate was killing the process correctly all along; only the HTTP flow was
     * wrong.
     *
     * <p>So the assertion is now the strong one: a genuine TIME_LIMIT_EXCEEDED, and
     * quickly. The generous ceiling is there to absorb a queued judge, not to admit
     * a hang - the polling budget bounds that separately.
     */
    @Test
    @DisplayName("an infinite loop is reported as TIME_LIMIT_EXCEEDED, promptly")
    void infiniteLoopIsTimeLimitExceeded() {
        long startedAt = System.nanoTime();

        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "while True:\n    pass", List.of(test("1", "1")));

        long elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000L;

        assertThat(report.status()).isEqualTo(ExecutionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(report.allPassed()).isFalse();
        assertThat(elapsedSeconds)
                .as("the verdict should arrive in seconds, not at a timeout")
                .isLessThan(25L);
    }

    // ------------------------------------------------------------------ hardening

    @Test
    @DisplayName("runaway output is truncated rather than returned whole")
    void runawayOutputIsTruncated() {
        // Large enough to prove truncation, small enough not to monopolise the
        // judge's worker pool for the rest of this class.
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "print('x'*500000)", List.of(test("1", "1")));

        // Whatever the sandbox does with it, nothing unbounded reaches a response.
        assertThat(report.status()).isNotEqualTo(ExecutionStatus.ACCEPTED);
        report.cases().forEach(result ->
                assertThat(result.actualOutput() == null ? 0 : result.actualOutput().length())
                        .isLessThanOrEqualTo(64 * 1024 + 64));
    }

    @Test
    @DisplayName("the sandbox has no network")
    void sandboxHasNoNetwork() {
        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                "import urllib.request\n"
                        + "print(urllib.request.urlopen('http://example.com', timeout=3).status)",
                List.of(test("1", "200")));

        // Participant code must not be able to reach out of the box. Either it
        // fails or it times out; it must not print 200.
        assertThat(report.status()).isNotEqualTo(ExecutionStatus.ACCEPTED);
    }

    @Test
    @DisplayName("source that is empty is refused without calling the judge")
    void emptySourceShortCircuits() {
        ExecutionReport report = engine.execute(
                ArenaLanguage.PYTHON, "   ", List.of(test("1", "1")));

        assertThat(report.status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(report.cases()).isEmpty();
    }

    @Test
    @DisplayName("an unreachable judge raises an outage, not a verdict")
    void unreachableJudgeRaisesOutage() {
        // Port 1 is reserved and nothing listens there.
        ExecutionEngine broken = new Judge0ExecutionEngine(
                new RestTemplateBuilder(),
                new Judge0Properties("http://127.0.0.1:1", null,
                        2.0, 5.0, 256 * 1024, 4096, 500, 1000, 50, 50, 500));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        broken.execute(ArenaLanguage.PYTHON, "print(1)", List.of(test("", "1"))))
                .isInstanceOf(ExecutionUnavailableException.class);
    }

    @Test
    @DisplayName("concurrent executions against the real judge stay independent")
    void concurrentExecutionsAreIndependent() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(5);
        try {
            var jobs = List.of(1, 2, 3, 4, 5).stream()
                    .<java.util.concurrent.Callable<Boolean>>map(n -> () -> {
                        ExecutionReport report = engine.execute(ArenaLanguage.PYTHON,
                                "print(int(input())*2)",
                                List.of(test(String.valueOf(n), String.valueOf(n * 2))));
                        return report.allPassed();
                    })
                    .toList();

            for (var future : pool.invokeAll(jobs)) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
