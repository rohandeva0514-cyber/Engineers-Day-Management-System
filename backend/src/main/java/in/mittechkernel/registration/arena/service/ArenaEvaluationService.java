package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.DebugProblem;
import in.mittechkernel.registration.arena.bank.Difficulty;
import in.mittechkernel.registration.arena.bank.ProblemBankService;
import in.mittechkernel.registration.arena.dto.ProblemDtos;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionDetail;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionList;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmissionRow;
import in.mittechkernel.registration.arena.dto.admin.EvaluationDtos.SubmittedProblem;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.entity.AttemptProblem;
import in.mittechkernel.registration.arena.repository.ArenaAttemptRepository;
import in.mittechkernel.registration.arena.repository.AttemptProblemRepository;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manual evaluation, for organisers.
 *
 * <p>The emergency model this supports:
 *
 * <pre>
 *   participant code -> autosaved -> final submission -> locked
 *                                                          |
 *                     organiser reads the code  &lt;-----------
 *                                |
 *                     awards 0 or full points per problem
 *                                |
 *                     organiser declares the winner
 * </pre>
 *
 * <p>Nothing here executes, compares, or judges anything. There is deliberately no
 * method that could decide a submission is correct: correctness is a person's
 * judgement in this mode, and a system that guessed at it would be worse than one
 * that does not try.
 *
 * <p>Nor is there a winner. The standings are ordered by score and then by submission
 * time - the documented tiebreak - but the last step stays an organiser's decision.
 */
@Service
public class ArenaEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ArenaEvaluationService.class);

    private final ArenaAttemptRepository attemptRepository;
    private final AttemptProblemRepository slotRepository;
    private final ProblemBankService bank;

    public ArenaEvaluationService(ArenaAttemptRepository attemptRepository,
                                  AttemptProblemRepository slotRepository,
                                  ProblemBankService bank) {
        this.attemptRepository = attemptRepository;
        this.slotRepository = slotRepository;
        this.bank = bank;
    }

    // ------------------------------------------------------------------- listing

    /**
     * Everyone with something to evaluate.
     *
     * <p>Includes participants whose clock ran out without submitting. Their code is
     * autosaved and still has to be marked, and quietly excluding them would mean
     * losing a student's work to a technicality.
     */
    @Transactional(readOnly = true)
    public SubmissionList submissions() {
        List<ArenaAttempt> attempts = attemptRepository.findForEvaluation();
        List<SubmissionRow> rows = attempts.stream().map(this::rowFor).toList();
        return new SubmissionList(rows.size(), rows);
    }

    /** One submission, with every problem and the code as it was left. */
    @Transactional(readOnly = true)
    public SubmissionDetail submission(Long attemptId) {
        ArenaAttempt attempt = requireAttempt(attemptId);
        ArenaLanguage language = attempt.languageOrNull();

        List<SubmittedProblem> problems = new ArrayList<>();
        for (AttemptProblem slot : slotRepository.findBoard(attemptId)) {
            Optional<DebugProblem> problem = language == null
                    ? Optional.empty()
                    : bank.find(language, slot.getRef());

            problems.add(new SubmittedProblem(
                    slot.getRef(),
                    problem.map(DebugProblem::title).orElse(slot.getProblemId()),
                    slot.getDifficulty(),
                    pointsFor(slot),
                    slot.getStatus().name(),
                    slot.hasDraft(),
                    // The autosaved draft IS the submission. There is no second copy.
                    slot.getDraftCode(),
                    slot.getAwardedPoints(),

                    // What the problem asked for, so the reviewer can actually judge
                    // the code beside it. Read from the bank that is already loaded
                    // above - no extra query, and nothing new is exposed.
                    problem.map(DebugProblem::problemStatement).orElse(null),
                    problem.map(DebugProblem::inputFormat).orElse(null),
                    problem.map(DebugProblem::outputFormat).orElse(null),
                    problem.map(DebugProblem::constraints).orElse(null),
                    problem.map(ArenaEvaluationService::visibleTestsOf).orElse(List.of())));
        }

        return new SubmissionDetail(rowFor(attempt), problems);
    }

    // ------------------------------------------------------------------ awarding

    /**
     * Award points for one problem, by hand.
     *
     * <p>Accepts 0 or the problem's full value and nothing between - the scoring rules
     * define no partial credit, and inventing some here would make results
     * unexplainable. Null clears an award, so a mis-click can be undone rather than
     * needing a database edit.
     *
     * <p>The attempt total is recomputed from the awards rather than accumulated, so
     * it cannot drift out of step with them however many times an organiser changes
     * their mind.
     */
    @Transactional
    public SubmissionDetail award(Long attemptId, String ref, Integer points, String actor) {
        ArenaAttempt attempt = requireAttempt(attemptId);

        AttemptProblem slot = slotRepository
                .findSlot(attemptId, ref == null ? "" : ref.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(ApiErrorCode.PROBLEM_NOT_FOUND,
                        "No such problem in that submission.",
                        Map.of("ref", String.valueOf(ref))));

        int full = pointsFor(slot);
        if (points != null && points != 0 && points != full) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "Award either 0 or the problem's full value.",
                    Map.of("allowed", List.of(0, full), "difficulty", slot.getDifficulty()));
        }

        Instant now = Instant.now();
        slot.award(points, now);
        slotRepository.save(slot);

        recomputeTotal(attempt);
        attemptRepository.save(attempt);

        log.info("Attempt {} problem {} awarded {} by {}", attemptId, slot.getRef(), points, actor);
        return submission(attemptId);
    }

    /**
     * Recompute an attempt's total from its awards.
     *
     * <p>Stays null while nothing has been evaluated, so "not marked yet" and "marked
     * zero" remain distinguishable on the standings screen.
     */
    private void recomputeTotal(ArenaAttempt attempt) {
        List<AttemptProblem> slots = slotRepository.findBoard(attempt.getId());

        boolean anyEvaluated = slots.stream().anyMatch(AttemptProblem::isEvaluated);
        if (!anyEvaluated) {
            attempt.setScore(null);
            return;
        }
        attempt.setScore(slots.stream()
                .map(AttemptProblem::getAwardedPoints)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
    }

    // ------------------------------------------------------------------ helpers

    private SubmissionRow rowFor(ArenaAttempt attempt) {
        Participant participant = attempt.getParticipant();
        List<AttemptProblem> slots = slotRepository.findBoard(attempt.getId());

        int evaluated = (int) slots.stream().filter(AttemptProblem::isEvaluated).count();

        return new SubmissionRow(
                attempt.getId(),
                participant.getFullName(),
                participant.getRollNo(),
                participant.getEmail(),
                participant.getBranch(),
                participant.getDivision(),
                participant.getYearLevel(),
                attempt.getLanguage(),
                // The STORED state, not the effective one: an organiser needs to see
                // that someone never pressed submit, rather than a derived EXPIRED
                // that hides it.
                attempt.getState().name(),
                attempt.getFinalizedAt(),
                attempt.getScore(),
                evaluationStatus(evaluated, slots.size()),
                evaluated,
                slots.size());
    }

    private static String evaluationStatus(int evaluated, int total) {
        if (total == 0 || evaluated == 0) {
            return "PENDING";
        }
        return evaluated == total ? "EVALUATED" : "PARTIAL";
    }

    /**
     * A problem's worked examples, for the reviewer.
     *
     * <p>{@code visibleTests()} only. The hidden set is the same shape, which is
     * exactly why this names its source rather than accepting a list from a caller
     * who could pass the wrong one.
     */
    private static List<ProblemDtos.VisibleTest> visibleTestsOf(DebugProblem problem) {
        return problem.visibleTests().stream()
                .map(test -> new ProblemDtos.VisibleTest(test.input(), test.expectedOutput()))
                .toList();
    }

    /** The full value of a problem, from its difficulty. 100 / 200 / 300. */
    private static int pointsFor(AttemptProblem slot) {
        return Difficulty.parse(slot.getDifficulty()).map(Difficulty::points).orElse(0);
    }

    private ArenaAttempt requireAttempt(Long attemptId) {
        return attemptRepository.findByIdWithDetail(attemptId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ATTEMPT_NOT_FOUND,
                        "No such submission.", Map.of("attemptId", String.valueOf(attemptId))));
    }

    /** Every submission, flattened for export. Used by the CSV writer. */
    @Transactional(readOnly = true)
    public List<SubmissionDetail> allSubmissions() {
        return attemptRepository.findForEvaluation().stream()
                .map(attempt -> submission(attempt.getId()))
                .toList();
    }
}
