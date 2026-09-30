package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.bank.DebugProblem;
import in.mittechkernel.registration.arena.bank.Difficulty;
import in.mittechkernel.registration.arena.bank.ProblemBankService;
import in.mittechkernel.registration.arena.dto.ProblemDtos.DraftSavedResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemBoardResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemDetailResponse;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemSummary;
import in.mittechkernel.registration.arena.dto.ProblemDtos.ProblemZone;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.entity.AttemptProblem;
import in.mittechkernel.registration.arena.entity.AttemptState;
import in.mittechkernel.registration.arena.repository.ArenaAttemptRepository;
import in.mittechkernel.registration.arena.repository.AttemptProblemRepository;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The debugging workspace: the board, one problem, and draft persistence.
 *
 * <h2>Language is taken, never accepted</h2>
 *
 * <p>Every method here derives the language from the authenticated attempt. There is
 * no language parameter on any endpoint, no language field in any request body, and
 * no code path that reads one from a client. A participant locked to Java cannot
 * express a request for the Python bank, because there is nowhere in the API to put
 * the word "python".
 *
 * <h2>Problem ownership is a lookup, not a check</h2>
 *
 * <p>A participant names a board handle - {@code E-02}. Resolving it requires both
 * the attempt (to find the row) and the language (to find the bank entry), and both
 * come from the session. Reaching another participant's draft or another language's
 * problem is not refused after the fact; it is unreachable.
 */
@Service
public class ArenaWorkspaceService {

    /**
     * Ceiling on a saved draft.
     *
     * <p>The largest buggy program in the bank is under 700 characters, so 64 KB is
     * far beyond any legitimate edit. It is here because a draft endpoint that
     * accepts unbounded text is a way to fill the database from a browser, and
     * because a participant who pastes their entire node_modules by accident should
     * get a clear refusal rather than a timeout.
     */
    private static final int MAX_DRAFT_BYTES = 64 * 1024;

    private final ProblemBankService bank;
    private final ArenaAttemptRepository attemptRepository;
    private final AttemptProblemRepository slotRepository;
    private final ArenaControlService control;

    public ArenaWorkspaceService(ProblemBankService bank,
                                 ArenaAttemptRepository attemptRepository,
                                 AttemptProblemRepository slotRepository,
                                 ArenaControlService control) {
        this.bank = bank;
        this.attemptRepository = attemptRepository;
        this.slotRepository = slotRepository;
        this.control = control;
    }

    // -------------------------------------------------------------- the board

    /**
     * All twelve problems, in three zones, all open.
     *
     * <p>There is no progression to compute because there is none to enforce. Every
     * zone is returned unconditionally and none carries a locked flag, so a client
     * has nothing it could render as gated even if it wanted to.
     */
    @Transactional
    public ProblemBoardResponse board(Long attemptId) {
        ArenaAttempt attempt = requireRunningAttempt(attemptId);
        ArenaLanguage language = languageOf(attempt);

        List<AttemptProblem> slots = materialiseIfNeeded(attempt, language);
        Map<String, AttemptProblem> byRef = new LinkedHashMap<>();
        slots.forEach(slot -> byRef.put(slot.getRef(), slot));

        Instant now = Instant.now();
        List<ProblemZone> zones = new ArrayList<>();

        for (Difficulty difficulty : Difficulty.values()) {
            List<ProblemSummary> summaries = bank.board(language).stream()
                    .filter(problem -> problem.difficulty() == difficulty)
                    .map(problem -> {
                        AttemptProblem slot = byRef.get(problem.ref());
                        return slot == null ? null : ProblemMapper.summary(problem, slot);
                    })
                    .filter(summary -> summary != null)
                    .toList();
            zones.add(new ProblemZone(difficulty.name(), difficulty.points(), summaries));
        }

        attempt.touch(now);
        attemptRepository.save(attempt);

        return new ProblemBoardResponse(
                now,
                language.id(),
                attempt.remainingSeconds(now),
                attempt.getExpiresAt(),
                (int) slotRepository.countSolved(attemptId),
                slots.size(),
                zones);
    }

    // ------------------------------------------------------------- one problem

    @Transactional
    public ProblemDetailResponse problem(Long attemptId, String ref) {
        ArenaAttempt attempt = requireRunningAttempt(attemptId);
        ArenaLanguage language = languageOf(attempt);

        materialiseIfNeeded(attempt, language);

        AttemptProblem slot = requireSlot(attemptId, ref);
        DebugProblem problem = requireBankProblem(language, slot.getRef());

        Instant now = Instant.now();
        attempt.touch(now);
        attemptRepository.save(attempt);

        // Opening a problem changes nothing. A participant browsing the board is not
        // attempting anything, and a status that moved on read would make the board
        // a record of curiosity rather than of work.
        return ProblemMapper.detail(problem, slot, now);
    }

    // ------------------------------------------------------------------ drafts

    /**
     * Persist what the participant has typed.
     *
     * <p>Server-side so that a refresh, a crash, or a move to another machine does
     * not cost them their work - the same reason the attempt itself is server-side.
     *
     * <p>A save is emphatically not a submission: it runs nothing, judges nothing,
     * and can never mark a problem solved. The most it does is move an untouched
     * problem to ATTEMPTED.
     *
     * <p>{@code revision} is an optional optimistic guard. When supplied and stale,
     * the save is refused rather than applied - so a tab left open on an old device
     * cannot overwrite work done since on another one. Omitting it means "I do not
     * know what I am overwriting", which is fine for a first save and for a client
     * that has just loaded the problem.
     */
    @Transactional
    public DraftSavedResponse saveDraft(Long attemptId, String ref, String code, Integer revision) {
        ArenaAttempt attempt = requireRunningAttempt(attemptId);
        ArenaLanguage language = languageOf(attempt);

        materialiseIfNeeded(attempt, language);
        AttemptProblem slot = requireSlot(attemptId, ref);

        // Confirms the handle names a real problem in THIS participant's language
        // before anything is written.
        requireBankProblem(language, slot.getRef());

        // A submitted problem is final. Allowing an edit afterwards would mean the
        // code on record stops matching the code that was judged.
        if (slot.isSubmitted()) {
            throw problemAlreadySubmitted();
        }

        if (code == null) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                    "Draft code is required.", Map.of());
        }
        if (code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DRAFT_BYTES) {
            throw new ApiException(ApiErrorCode.DRAFT_TOO_LARGE,
                    "That draft is too large to save.",
                    Map.of("maxBytes", MAX_DRAFT_BYTES));
        }

        if (revision != null && revision != slot.getDraftRevision()) {
            throw new ApiException(ApiErrorCode.DRAFT_STALE,
                    "This problem was saved somewhere else since you loaded it. "
                            + "Reload the problem to see the current code.",
                    Map.of("currentRevision", slot.getDraftRevision()));
        }

        Instant now = Instant.now();
        slot.saveDraft(code, now);
        slotRepository.save(slot);

        attempt.touch(now);
        attemptRepository.save(attempt);

        return new DraftSavedResponse(
                slot.getRef(), slot.getDraftRevision(), slot.getStatus().name(), now);
    }

    /**
     * One problem, fully resolved and fully authorised.
     *
     * <p>Every guard in this service applied in one call: arena ACTIVE, attempt
     * running, slot belongs to this attempt, bank entry exists in the attempt's
     * locked language. {@code ArenaExecutionService} uses this rather than
     * re-implementing the checks - a second copy of an authorisation chain is a
     * second chance to get it subtly wrong, and only one of the two would be
     * getting reviewed.
     */
    @Transactional
    public ResolvedProblem resolve(Long attemptId, String ref) {
        ArenaAttempt attempt = requireRunningAttempt(attemptId);
        ArenaLanguage language = languageOf(attempt);

        materialiseIfNeeded(attempt, language);

        AttemptProblem slot = requireSlot(attemptId, ref);
        DebugProblem problem = requireBankProblem(language, slot.getRef());

        return new ResolvedProblem(attempt, slot, problem, language);
    }

    /**
     * An authorised problem in context.
     *
     * <p>Carries the full {@link DebugProblem}, answer material included, because
     * the judge needs the hidden tests. It is an internal value passed between
     * services and is never a response body - the mappers decide what of it a
     * participant sees.
     */
    public record ResolvedProblem(
            ArenaAttempt attempt,
            AttemptProblem slot,
            DebugProblem problem,
            ArenaLanguage language) {
    }

    // ------------------------------------------------------------------ guards

    /**
     * Load the attempt and insist the mission is actually running.
     *
     * <p>The workspace is mission content. An attempt that has not started has no
     * board yet; one that is over must not gain another edit. Expiry is read from
     * {@code effectiveState}, so a lapsed deadline closes the workspace even though
     * Phase E has not yet written the transition down.
     */
    private ArenaAttempt requireRunningAttempt(Long attemptId) {
        control.requireActive();

        ArenaAttempt attempt = attemptRepository.findByIdWithDetail(attemptId)
                .orElseThrow(ArenaSessionService::invalidSession);

        AttemptState effective = attempt.effectiveState(Instant.now());

        if (effective == AttemptState.INITIALIZED) {
            throw new ApiException(ApiErrorCode.ATTEMPT_NOT_STARTED,
                    "Your mission has not started yet.",
                    Map.of("state", effective.name()));
        }
        if (effective != AttemptState.ACTIVE) {
            throw new ApiException(ApiErrorCode.ATTEMPT_ALREADY_FINALIZED,
                    "Your mission is over.",
                    Map.of("state", effective.name()));
        }
        return attempt;
    }

    private ArenaLanguage languageOf(ArenaAttempt attempt) {
        ArenaLanguage language = attempt.languageOrNull();
        if (language == null) {
            // Unreachable: ck_arena_attempt_started makes an ACTIVE attempt without a
            // language impossible to store. Checked because the alternative to this
            // branch is a NullPointerException in a mapper.
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "This attempt has no language recorded.", Map.of());
        }
        return language;
    }

    private AttemptProblem requireSlot(Long attemptId, String ref) {
        String normalised = ref == null ? "" : ref.trim().toUpperCase();
        return slotRepository.findSlot(attemptId, normalised)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PROBLEM_NOT_FOUND,
                        "No such problem in your mission.",
                        Map.of("ref", normalised)));
    }

    /** Shared by the draft path and the execution path; one wording, one meaning. */
    public static ApiException problemAlreadySubmitted() {
        return new ApiException(ApiErrorCode.PROBLEM_ALREADY_SUBMITTED,
                "You have already submitted this problem. It is final.",
                Map.of());
    }

    private DebugProblem requireBankProblem(ArenaLanguage language, String ref) {
        return bank.find(language, ref)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PROBLEM_NOT_FOUND,
                        "No such problem in your mission.",
                        Map.of("ref", ref)));
    }

    // ---------------------------------------------------------- materialisation

    /**
     * Ensure this attempt has its twelve board slots.
     *
     * <p>Normally a no-op: {@code ArenaAttemptService.start} creates them inside the
     * same transaction that starts the mission. This exists for the attempt that
     * started before the board existed - during development, or across a deploy that
     * lands mid-event - where the alternative is a participant staring at an empty
     * board with no way to recover.
     *
     * <p>Deterministic and idempotent. The problems are the bank's twelve for the
     * locked language, in bank order, every time; and if two requests race, the
     * UNIQUE constraints refuse the loser, which then simply reads what the winner
     * wrote.
     */
    private List<AttemptProblem> materialiseIfNeeded(ArenaAttempt attempt, ArenaLanguage language) {
        List<AttemptProblem> existing = slotRepository.findBoard(attempt.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        try {
            return materialise(attempt, language);
        } catch (DataIntegrityViolationException lostTheRace) {
            return slotRepository.findBoard(attempt.getId());
        }
    }

    /**
     * Create the twelve slots for an attempt. Called once, at Start Mission.
     *
     * <p>Public so {@code ArenaAttemptService} can call it in the same transaction
     * that flips the attempt to ACTIVE - a board that appeared a moment later than
     * the clock would be a window in which a participant has time running and
     * nothing to work on.
     */
    @Transactional
    public List<AttemptProblem> materialise(ArenaAttempt attempt, ArenaLanguage language) {
        Instant now = Instant.now();
        List<AttemptProblem> slots = bank.board(language).stream()
                .map(problem -> new AttemptProblem(attempt, problem, now))
                .toList();
        return slotRepository.saveAll(slots);
    }
}
