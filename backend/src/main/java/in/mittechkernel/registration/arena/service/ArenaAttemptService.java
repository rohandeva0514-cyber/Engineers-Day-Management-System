package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.dto.ArenaDtos;
import in.mittechkernel.registration.arena.dto.ArenaDtos.AttemptStateResponse;
import in.mittechkernel.registration.arena.dto.ArenaDtos.AttemptView;
import in.mittechkernel.registration.arena.dto.ArenaDtos.ParticipantIdentity;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.entity.ArenaControl;
import in.mittechkernel.registration.arena.entity.AttemptState;
import in.mittechkernel.registration.arena.repository.ArenaAttemptRepository;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The attempt's own lifecycle: read it, choose a language, start the mission.
 *
 * <p>Language rules live here rather than in a service of their own, and that is a
 * deliberate reading of "one responsibility". The language is not an independent
 * setting that happens to be validated - it is a field of the attempt whose
 * mutability *is* the attempt's state. A separate LanguageService would have to ask
 * this class whether the attempt had started before every write, which means the
 * rule would live here anyway with an extra hop in front of it.
 */
@Service
public class ArenaAttemptService {

    private static final Logger log = LoggerFactory.getLogger(ArenaAttemptService.class);

    private final ArenaAttemptRepository attemptRepository;
    private final ArenaControlService control;
    private final ArenaSessionService sessions;
    private final ArenaWorkspaceService workspace;

    public ArenaAttemptService(ArenaAttemptRepository attemptRepository,
                               ArenaControlService control,
                               ArenaSessionService sessions,
                               ArenaWorkspaceService workspace) {
        this.attemptRepository = attemptRepository;
        this.control = control;
        this.sessions = sessions;
        this.workspace = workspace;
    }

    /**
     * Everything needed to rebuild the screen after a refresh.
     *
     * <p>Deliberately does NOT call {@link ArenaControlService#requireActive()}. A
     * participant whose arena has just been ended still has to be told what happened,
     * and refusing the one request that would tell them would leave the page stuck on
     * whatever it was showing. The arena's status is reported instead, and the client
     * renders the ended screen from it.
     */
    @Transactional
    public AttemptStateResponse state(Long attemptId) {
        ArenaAttempt attempt = load(attemptId);
        Instant now = Instant.now();
        attempt.touch(now);
        attemptRepository.save(attempt);

        ArenaControl arena = control.current();
        return new AttemptStateResponse(
                now,
                arena.getDurationSeconds(),
                arena.getStatus().name(),
                ParticipantIdentity.from(attempt.getRegistration()),
                AttemptView.from(attempt, now),
                ArenaDtos.languages());
    }

    /**
     * Record a tentative language choice, before the mission starts.
     *
     * <p>Persisted rather than held in the browser so that a refresh - or a move to
     * another machine - restores the screen the participant was actually on. It
     * commits them to nothing: they may change it as often as they like until they
     * press Start Mission.
     */
    @Transactional
    public AttemptStateResponse chooseLanguage(Long attemptId, String rawLanguage) {
        control.requireActive();
        ArenaAttempt attempt = load(attemptId);

        // A language-specific refusal rather than the generic "already started" one:
        // a participant who hits this has pressed a stale picker, and the thing they
        // need told is that the choice is fixed, not that the mission is running.
        if (attempt.effectiveState(Instant.now()) != AttemptState.INITIALIZED) {
            throw languageLocked();
        }

        attempt.chooseLanguage(requireSupported(rawLanguage));
        attemptRepository.save(attempt);

        return state(attemptId);
    }

    /**
     * Start the mission. This is the moment the 45 minutes begins.
     *
     * <p>The deadline is computed here, from the server's clock and the arena's
     * configured duration, and stamped onto the row once. It is never recomputed:
     * an organiser changing the duration mid-event moves the finish line for nobody
     * who is already running.
     *
     * <p>The transition itself is a conditional UPDATE, not a read-modify-write. Two
     * requests arriving together - a double-click, or a retry on a flaky hall
     * network - would otherwise both read INITIALIZED and both write ACTIVE, and the
     * second would silently restart the clock. Exactly one wins; the other re-reads
     * and is told the mission is already running.
     */
    @Transactional
    public AttemptStateResponse start(Long attemptId, String rawLanguage) {
        control.requireActive();
        ArenaAttempt attempt = load(attemptId);

        Instant now = Instant.now();
        requireNotStarted(attempt, now);

        ArenaLanguage language = requireSupported(rawLanguage);
        int durationSeconds = control.current().getDurationSeconds();

        Instant expiresAt = now.plus(Duration.ofSeconds(durationSeconds));
        Instant sessionExpiry = sessions.missionExpiry(expiresAt);

        int started = attemptRepository.tryStart(
                attempt.getId(), language.id(), now, expiresAt, sessionExpiry);

        if (started == 0) {
            // Someone else won the race, or the state moved underneath us. Re-read
            // and let the same guard produce the right refusal rather than guessing.
            requireNotStarted(load(attemptId), Instant.now());
            // Still INITIALIZED after a failed conditional update means something is
            // wrong that a retry will not fix.
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "The mission could not be started. Nothing was changed.", Map.of());
        }

        // The board is created in the same transaction that starts the clock. A
        // board that appeared a moment later would be a window in which the
        // participant has time running and nothing to work on.
        workspace.materialise(load(attemptId), language);

        log.info("Arena attempt {} started in {}, expires at {}",
                attemptId, language.id(), expiresAt);

        return state(attemptId);
    }

    /**
     * Final mission submission. One per attempt, irreversible.
     *
     * <p>Submits what the server already holds. The request carries no code at all -
     * the authoritative content is whatever autosave has persisted into
     * {@code attempt_problem.draft_code}, so a client cannot submit something it never
     * saved, and a lost final keystroke cannot be smuggled in past the draft's own
     * validation. The frontend flushes its pending save and waits for the
     * acknowledgement before calling this; if that flush failed, the participant
     * submits their last successfully saved state, which is the honest outcome.
     *
     * <p>The timestamp is the server's, taken here and written inside the same
     * conditional UPDATE that moves the state - so the recorded time is the moment
     * the submission actually took effect, not a moment a client proposed. That
     * timestamp is the tiebreak, which is precisely why it must not be influenced
     * from outside.
     */
    @Transactional
    public AttemptStateResponse submitMission(Long attemptId) {
        control.requireActive();

        Instant now = Instant.now();
        ArenaAttempt attempt = load(attemptId);
        requireSubmittable(attempt, now);

        int finalised = attemptRepository.tryFinalize(attemptId, now);

        if (finalised == 0) {
            // Lost a race, or the deadline passed between the guard and the write.
            // Re-read and let the same guard produce the right refusal.
            requireSubmittable(load(attemptId), Instant.now());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR,
                    "The mission could not be submitted. Nothing was changed.", Map.of());
        }

        log.info("Arena attempt {} submitted for manual evaluation at {}", attemptId, now);
        return state(attemptId);
    }

    // ------------------------------------------------------------------ guards

    /**
     * Refuse a submission that cannot legally happen.
     *
     * <p>Three distinct refusals because they need three different screens: a mission
     * that never started, one already finished, and one whose clock has run out.
     */
    private void requireSubmittable(ArenaAttempt attempt, Instant now) {
        AttemptState effective = attempt.effectiveState(now);

        if (effective == AttemptState.ACTIVE) {
            return;
        }
        if (effective == AttemptState.INITIALIZED) {
            throw new ApiException(ApiErrorCode.ATTEMPT_NOT_STARTED,
                    "Your mission has not started yet.",
                    Map.of("state", effective.name()));
        }
        throw new ApiException(ApiErrorCode.ATTEMPT_ALREADY_FINALIZED,
                effective == AttemptState.SUBMITTED
                        ? "Your mission has already been submitted."
                        : "Your mission is over.",
                Map.of("state", effective.name()));
    }

    /**
     * Refuse anything that would change an attempt which has left INITIALIZED.
     *
     * <p>Two distinct refusals, because they need two different screens: a running
     * mission sends the participant back into it, a finished one cannot be left.
     * Expiry is read from {@code effectiveState}, so an attempt whose deadline has
     * passed is treated as finished even though Phase E has not yet written that
     * down.
     */
    private void requireNotStarted(ArenaAttempt attempt, Instant now) {
        AttemptState effective = attempt.effectiveState(now);

        if (effective == AttemptState.INITIALIZED) {
            return;
        }
        if (effective == AttemptState.ACTIVE) {
            throw new ApiException(ApiErrorCode.ATTEMPT_ALREADY_STARTED,
                    "Your mission is already running.",
                    Map.of("state", effective.name()));
        }
        throw new ApiException(ApiErrorCode.ATTEMPT_ALREADY_FINALIZED,
                "Your mission is already over.",
                Map.of("state", effective.name()));
    }

    /**
     * Accept only one of the five known languages.
     *
     * <p>The client never gets to name a problem bank. An arbitrary string here would
     * decide which problems a participant is scored against, so it is resolved
     * against the enum and refused otherwise - never trimmed, defaulted, or passed
     * through.
     */
    private ArenaLanguage requireSupported(String raw) {
        return ArenaLanguage.fromId(raw)
                .orElseThrow(() -> new ApiException(ApiErrorCode.LANGUAGE_NOT_SUPPORTED,
                        "That is not one of the available debugging languages.",
                        Map.of("allowed", ArenaLanguage.ids())));
    }

    /**
     * Refuse a language change once the mission has started.
     *
     * <p>Separate from {@link #requireNotStarted} because the message is about the
     * language specifically, and because a participant hitting this has usually
     * pressed a stale button rather than done anything wrong.
     */
    public static ApiException languageLocked() {
        return new ApiException(ApiErrorCode.ARENA_LANGUAGE_LOCKED,
                "Your language was locked when the mission started and cannot be changed.",
                Map.of());
    }

    /**
     * Load the attempt, with the participant and event already attached.
     *
     * <p>Always inside this service's own transaction, never handed in from a
     * controller. {@code open-in-view} is off, so an entity loaded outside a
     * transaction arrives detached and every lazy association on it throws the
     * moment a response tries to read it.
     *
     * <p>A missing attempt is reported as an invalid session rather than as a 404:
     * the only way to name an attempt is to hold a session for it, so an id that
     * resolves to nothing means the session is the thing that is wrong.
     */
    private ArenaAttempt load(Long attemptId) {
        return attemptRepository.findByIdWithDetail(attemptId)
                .orElseThrow(ArenaSessionService::invalidSession);
    }
}
