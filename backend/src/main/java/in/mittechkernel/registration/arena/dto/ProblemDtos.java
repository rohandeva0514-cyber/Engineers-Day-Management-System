package in.mittechkernel.registration.arena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Everything a participant is allowed to see about a problem.
 *
 * <h2>Read the field lists as the security boundary</h2>
 *
 * <p>These records are the reason answer material cannot leak. Not a filter applied
 * on the way out, not a set of {@code @JsonIgnore} annotations on the full model -
 * separate types with <b>no field capable of holding</b> a corrected solution, a
 * bug location, a bug description, a why-wrong note, a hidden test, or a count of
 * hidden tests.
 *
 * <p>Leaking one would therefore require adding a field here and populating it,
 * which is a deliberate act a reviewer would see, rather than forgetting to strip
 * one, which is an omission nobody sees. {@code ProblemLeakageTest} serialises every
 * one of these for all 60 problems and fails on the presence of any of it.
 *
 * <p>Note what is absent even though it would be harmless-looking: there is no
 * {@code hiddenTestCount}. Knowing a problem has six hidden tests tells a
 * participant how much is being checked, and decision 5 of the locked architecture
 * says that number never leaves the server in any form.
 */
public final class ProblemDtos {

    private ProblemDtos() {
    }

    /**
     * A worked example, shown with the problem.
     *
     * <p>Only ever built from a problem's {@code visible_tests}. Hidden tests are
     * structurally identical in the loaded model, which is exactly why the mapper
     * never accepts a list of them and this type is never constructed from one.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VisibleTest(String input, String expectedOutput) {
    }

    /** One tile on the board. Enough to choose what to work on, and no more. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProblemSummary(
            String ref,
            String title,
            String difficulty,
            int points,
            List<String> concepts,
            String status,
            boolean hasDraft) {
    }

    /** One difficulty zone. All three are always sent, and always unlocked. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProblemZone(
            String difficulty,
            int points,
            List<ProblemSummary> problems) {
    }

    /**
     * The whole board.
     *
     * <p>All three zones in one response. There is no progression to report because
     * there is none to enforce: every zone is open from the moment the mission
     * starts, and a client cannot show a locked tab because the server never
     * describes one.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProblemBoardResponse(
            Instant serverTime,
            String language,
            long remainingSeconds,
            Instant expiresAt,
            int solvedCount,
            int totalProblems,
            List<ProblemZone> zones) {
    }

    /**
     * One problem, opened.
     *
     * <p>{@code buggyCode} is the starting point as the bank wrote it;
     * {@code draftCode} is what this participant has since typed, or null if they
     * have not touched it. The client shows the draft when present and the buggy
     * code otherwise - which is what makes a refresh mid-edit lossless.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProblemDetailResponse(
            Instant serverTime,
            String ref,
            String title,
            String difficulty,
            int points,
            List<String> concepts,
            String problemStatement,
            String inputFormat,
            String outputFormat,
            String constraints,
            String language,
            String buggyCode,
            String draftCode,
            int draftRevision,
            String status,
            List<VisibleTest> visibleTests) {
    }

    /** Acknowledgement of an accepted draft save. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DraftSavedResponse(
            String ref,
            int revision,
            String status,
            Instant savedAt) {
    }

    /** Request body for a draft save. */
    public record DraftSaveRequest(String code, Integer revision) {
    }

    /* ------------------------------------------------------------- execution */

    /**
     * The only thing a client may send to run or submit code.
     *
     * <p>One field. There is deliberately nowhere to put a language, a Judge0
     * language id, a CPU limit, a memory limit, a problem id, an attempt id or a
     * participant id - every one of those is derived server-side, so none of them
     * can be proposed. The participant's source is the sole participant-controlled
     * input to execution.
     */
    public record ExecuteRequest(String code) {
    }

    /**
     * One visible test, as executed.
     *
     * <p>Safe because the input and expected output came from {@code visible_tests},
     * which the participant can already see on the problem. The hidden path never
     * constructs this type - see {@link SubmitResultResponse}.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestOutcome(
            int index,
            boolean passed,
            String status,
            String input,
            String expectedOutput,
            String actualOutput,
            Integer timeMs) {
    }

    /**
     * `POST /api/arena/problems/{ref}/run`
     *
     * <p>Visible tests only. Running code proves nothing about the hidden suite and
     * this response says nothing about it - there is no hidden count, no hidden
     * ratio, and no field that could carry one.
     *
     * <p>{@code passedCount}/{@code totalCount} refer to the VISIBLE tests, which
     * are already on screen, so they disclose nothing new.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RunResultResponse(
            Instant serverTime,
            String ref,
            String status,
            int passedCount,
            int totalCount,
            List<TestOutcome> tests,
            String compileOutput,
            String stderr,
            int runCount,
            String problemStatus) {
    }

    /**
     * `POST /api/arena/problems/{ref}/submit`
     *
     * <p>The hidden suite decides this, and the response reflects <b>none of its
     * shape</b>. No test list, no counts, no ratio - a boolean and a normalised
     * status, which is the minimum needed to tell a participant what happened.
     *
     * <p>A participant learning "solved: false, status: WRONG_ANSWER" learns that
     * their code is wrong. They do not learn how many hidden tests exist, how many
     * they failed, or which. That is decision 5, enforced by there being no field
     * for it rather than by a value being omitted.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubmitResultResponse(
            Instant serverTime,
            String ref,
            String status,
            boolean solved,
            Instant submittedAt,
            String compileOutput,
            String problemStatus) {
    }
}
