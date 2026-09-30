package in.mittechkernel.registration.arena.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.arena.dto.ProblemDtos;

import java.time.Instant;
import java.util.List;

/**
 * Organiser-facing payloads for manual evaluation.
 *
 * <p>These sit under the admin DTO package because they carry things the public API
 * never does - contact details, the participant's source code, awarded scores. The
 * package prefix is what SecurityConfig protects, so nothing here is one import away
 * from reaching a participant response.
 *
 * <p>Note what is still absent even here: no corrected code, no bug notes, no hidden
 * tests. An organiser reads those from the problem bank on disk, where they have
 * always lived. There is no reason to pipe them through an HTTP response, so these
 * records have no field for them.
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    /** One row of the submissions list. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubmissionRow(
            Long attemptId,
            String fullName,
            String rollNo,
            String email,
            String branch,
            String division,
            short yearLevel,
            String language,
            /** ACTIVE, SUBMITTED, EXPIRED or TERMINATED - see below. */
            String state,
            /** Null for a participant whose clock ran out without submitting. */
            Instant finalSubmittedAt,
            /** Sum of the awarded points, or null if nothing has been evaluated. */
            Integer totalScore,
            /** PENDING, PARTIAL or EVALUATED. Derived, never stored. */
            String evaluationStatus,
            int problemsEvaluated,
            int problemsTotal) {
    }

    /**
     * The submissions list.
     *
     * <p>Ordered by submission time, earliest first, which is the documented tiebreak
     * - so two equal scores already appear in the order the rules would rank them.
     * Nothing here says "winner": that call is an organiser's after reading the code.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubmissionList(int total, List<SubmissionRow> submissions) {
    }

    /**
     * One problem of one submission, with the code as the participant left it.
     *
     * <p>{@code sourceCode} is the autosaved draft - the same text the editor held.
     * Null means the participant never typed in this problem, which is worth showing
     * as distinct from empty.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubmittedProblem(
            String ref,
            String title,
            String difficulty,
            int points,
            String status,
            boolean attempted,
            String sourceCode,
            /** Null until an organiser awards something. */
            Integer awardedPoints,

            /*
             * What the problem actually asked for.
             *
             * Without these an organiser is shown a title and a block of code and
             * asked to decide whether it is correct - which is not a judgement anyone
             * can make. Manual evaluation needs the statement, the I/O contract and
             * the constraints in front of the reviewer, beside the code.
             *
             * Still deliberately ABSENT: corrected_code, bugs, hidden_tests. Those
             * remain in the bank on disk where an organiser can read them if they
             * want to; routing them through an HTTP response would put answer
             * material somewhere it has never needed to be.
             */
            String problemStatement,
            String inputFormat,
            String outputFormat,
            String constraints,
            /** The worked examples. Participant-visible already, so no new exposure. */
            List<ProblemDtos.VisibleTest> visibleTests) {
    }

    /** One participant's full submission, for review. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SubmissionDetail(
            SubmissionRow participant,
            List<SubmittedProblem> problems) {
    }

    /**
     * An award request.
     *
     * <p>{@code points} must be 0 or the problem's full value; the service refuses
     * anything else and the database refuses it again. The problem is named by its
     * board handle, and the attempt comes from the path - there is no participant id
     * or score in a body that could address someone else's submission.
     */
    public record AwardRequest(Integer points) {
    }
}
