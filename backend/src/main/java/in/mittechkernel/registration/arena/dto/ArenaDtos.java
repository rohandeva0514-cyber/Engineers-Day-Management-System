package in.mittechkernel.registration.arena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.entity.AttemptState;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Participant-facing arena payloads.
 *
 * <p>Every record here is read by a student, so the field lists are a security
 * decision rather than a convenience. The rule applied throughout: send what the
 * screen needs to render and nothing that merely happens to be nearby.
 */
public final class ArenaDtos {

    private ArenaDtos() {
    }

    /**
     * Who the system thinks is at the terminal.
     *
     * <p>Five fields, so a student can confirm "this is my registration" and stop.
     *
     * <p>Deliberately ABSENT: email, phone number, roll number, the internal
     * participant id, and the registration id. None of them are needed to recognise
     * yourself, and an arena session is held by whoever typed the code - which is
     * usually but not provably the person it belongs to.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ParticipantIdentity(
            String fullName,
            String branch,
            String division,
            short yearLevel,
            String eventName) {

        public static ParticipantIdentity from(Registration registration) {
            Participant participant = registration.getParticipant();
            return new ParticipantIdentity(
                    participant.getFullName(),
                    participant.getBranch(),
                    participant.getDivision(),
                    participant.getYearLevel(),
                    registration.getEvent().getName());
        }
    }

    /**
     * The attempt as its own participant may see it.
     *
     * <p>Deliberately ABSENT: {@code sessionTakeovers}, {@code score},
     * {@code solvedCount}, and the attempt id. The takeover count is an operational
     * signal for organisers, and the scores are Phase E's and are never shown to the
     * participant at all - reaching SUBMITTED says "recorded", never "you got 1700".
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AttemptView(
            String state,
            String language,
            boolean languageLocked,
            Instant startedAt,
            Instant expiresAt,
            long remainingSeconds) {

        public static AttemptView from(ArenaAttempt attempt, Instant now) {
            AttemptState effective = attempt.effectiveState(now);
            return new AttemptView(
                    effective.name(),
                    attempt.getLanguage(),
                    // Locked the moment the mission starts, and it stays locked
                    // through every terminal state. INITIALIZED is the only state in
                    // which a language may still change.
                    effective != AttemptState.INITIALIZED,
                    attempt.getStartedAt(),
                    attempt.getExpiresAt(),
                    attempt.remainingSeconds(now));
        }
    }

    /**
     * `POST /api/arena/access` — what a successful check-in returns.
     *
     * <p>The session token is in the body, not a URL and not a redirect. Query
     * strings end up in browser history, proxy logs, and referrer headers, and a
     * token that authorises a whole mission has no business in any of them.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AccessResponse(
            String sessionToken,
            Instant sessionExpiresAt,
            Instant serverTime,
            ParticipantIdentity participant,
            AttemptView attempt,
            List<ArenaStatusResponse.Language> languages) {
    }

    /**
     * `GET /api/arena/attempt` — the single rehydrate call.
     *
     * <p>One request restores the whole UI after a refresh: who you are, where you
     * are in the flow, how long is left, and what you may still choose. Splitting it
     * across three endpoints would mean three chances for the screen to render a
     * state the server has already moved past.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AttemptStateResponse(
            Instant serverTime,
            int durationSeconds,
            String arenaStatus,
            ParticipantIdentity participant,
            AttemptView attempt,
            List<ArenaStatusResponse.Language> languages) {
    }

    /** Request bodies. */
    public record LanguageChoice(String language) {
    }

    public record AccessRequest(String code) {
    }

    /** The five languages, in the one shape both status and attempt responses use. */
    public static List<ArenaStatusResponse.Language> languages() {
        return Arrays.stream(ArenaLanguage.values())
                .map(language -> new ArenaStatusResponse.Language(
                        language.id(), language.label(), language.runtime()))
                .toList();
    }
}
