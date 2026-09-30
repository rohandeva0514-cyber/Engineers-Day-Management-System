package in.mittechkernel.registration.arena.service;

import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import in.mittechkernel.registration.arena.repository.ArenaAttemptRepository;
import in.mittechkernel.registration.exception.ApiErrorCode;
import in.mittechkernel.registration.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Arena session tokens: mint, hash, resolve.
 *
 * <p>One job, and deliberately no knowledge of what an attempt is for. It does not
 * decide who may check in, when a mission starts, or whether a language is allowed -
 * it only answers "is this bearer token a live session, and whose".
 *
 * <h2>Why the token is opaque and not a JWT</h2>
 *
 * <p>A JWT would add a dependency, a signing key to rotate, and - the part that
 * matters here - a credential that stays valid until it expires because nothing can
 * revoke it. This arena needs revocation constantly: on final submission, when an
 * admin ends the arena, and every time a second device takes a session over. An
 * opaque token whose hash lives on the row it authorises is revoked by overwriting
 * one column.
 *
 * <h2>Why only a hash is stored</h2>
 *
 * <p>A database backup, a log of a query, or a read-only replica handed to someone
 * for debugging would otherwise contain live credentials for every running
 * participant. SHA-256 is the right primitive here rather than BCrypt: the token is
 * 256 bits of {@link SecureRandom} output, so there is no dictionary to attack and
 * no work factor worth paying on a lookup that happens on every keystroke once
 * drafts start saving.
 */
@Service
public class ArenaSessionService {

    /**
     * 32 bytes. The same reasoning {@code AccessCodeGenerator} documents for using
     * {@link SecureRandom} over {@link java.util.Random} applies with more force
     * here: this value authorises an entire mission, and a predictable one would let
     * anyone resume anyone's attempt.
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * How long a session lives before the mission starts.
     *
     * <p>Generous on purpose. Students check in as a group and then wait while an
     * organiser explains the rules, and a session that lapsed during the briefing
     * would send a hall full of people back to the code screen at once. Nothing is
     * at stake in this window - the clock has not started and the attempt is not
     * consumed - so the tight bound belongs on the running session, not this one.
     */
    private static final Duration PRE_MISSION_TTL = Duration.ofMinutes(45);

    /**
     * Slack added past the mission deadline.
     *
     * <p>The session has to outlive the mission it authorises, or the request that
     * carries the final submission at 00:03 remaining would be refused for having no
     * session rather than judged on its merits. Five minutes covers a slow network
     * and a laptop that suspended; the deadline itself is enforced separately and is
     * not softened by this.
     */
    private static final Duration POST_MISSION_GRACE = Duration.ofMinutes(5);

    private final ArenaAttemptRepository attemptRepository;
    private final SecureRandom random = new SecureRandom();

    public ArenaSessionService(ArenaAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /** A freshly minted token and the hash that will be stored in its place. */
    public record IssuedToken(String rawToken, String hash) {
    }

    /**
     * Mint a token.
     *
     * <p>URL-safe Base64 without padding, so it survives being put in a header
     * without escaping. It is never put in a URL - see the controllers - but a token
     * that cannot be transported safely is a bug waiting for the first person who
     * tries.
     */
    public IssuedToken mint() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(raw, hash(raw));
    }

    /** Validity for a session bound to an attempt that has not started yet. */
    public Instant preMissionExpiry(Instant now) {
        return now.plus(PRE_MISSION_TTL);
    }

    /** Validity for a session bound to a running mission: the deadline, plus slack. */
    public Instant missionExpiry(Instant missionDeadline) {
        return missionDeadline.plus(POST_MISSION_GRACE);
    }

    /**
     * Resolve a bearer token to the attempt it authorises.
     *
     * <p>Empty for every failure - unknown token, lapsed session, malformed value.
     * The caller turns that into one refusal, because distinguishing "no such
     * session" from "that session expired" tells someone probing the endpoint when
     * they have found something real.
     */
    @Transactional(readOnly = true)
    public Optional<ArenaAttempt> resolve(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return attemptRepository.findBySessionTokenHash(hash(rawToken))
                .filter(attempt -> attempt.hasLiveSession(now));
    }

    /** The standard refusal for anything wrong with a session. */
    public static ApiException invalidSession() {
        return new ApiException(ApiErrorCode.ARENA_SESSION_INVALID,
                "Your arena session is no longer valid. Enter your access code again.",
                Map.of());
    }

    /**
     * SHA-256, lower-case hex.
     *
     * <p>Matches {@code ck_arena_attempt_session_hash}, so a value that is not a hash
     * cannot reach the column even if some future caller passes the wrong thing.
     */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandated by the JLS for every conforming JVM.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
