package in.mittechkernel.registration.arena.entity;

import in.mittechkernel.registration.arena.ArenaLanguage;
import in.mittechkernel.registration.entity.Participant;
import in.mittechkernel.registration.entity.Registration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Duration;
import java.time.Instant;

/**
 * One participant's run at the Debugging Arena.
 *
 * <p>There is at most one of these per registration, enforced by a UNIQUE index
 * rather than by application logic - see V13. Every "did they already start"
 * question in the arena is answered from this row.
 */
@Entity
@Table(name = "arena_attempt")
public class ArenaAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, updatable = false)
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private AttemptState state;

    @Column(name = "language", length = 16)
    private String language;

    @Column(name = "session_token_hash", length = 64)
    private String sessionTokenHash;

    @Column(name = "session_issued_at")
    private Instant sessionIssuedAt;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "session_takeovers", nullable = false)
    private int sessionTakeovers;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "final_state_reason", length = 32)
    private String finalStateReason;

    /**
     * The participant's manually awarded total, or null before evaluation.
     *
     * <p>Derived from the per-problem awards rather than typed in, so it can never
     * disagree with them - see {@code ArenaEvaluationService.recomputeTotal}.
     */
    @Column(name = "score")
    private Integer score;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArenaAttempt() {
        // for JPA
    }

    /**
     * A fresh check-in.
     *
     * <p>Starts INITIALIZED with no language, no start and no deadline. Nothing here
     * commits the student to anything - that is the point of the state.
     */
    public ArenaAttempt(Registration registration, Participant participant) {
        this.registration = registration;
        this.participant = participant;
        this.state = AttemptState.INITIALIZED;
        this.sessionTakeovers = 0;
    }

    // ---------------------------------------------------------------- session

    /**
     * Bind a new session to this attempt.
     *
     * <p>Replacing an existing hash is what makes takeover work: the previous token
     * stops resolving the instant this is saved, so two people holding one access
     * code cannot work in parallel. The counter is incremented only when a session
     * was actually displaced, so a first check-in reads zero.
     */
    public void bindSession(String tokenHash, Instant issuedAt, Instant expiresAt) {
        if (this.sessionTokenHash != null && !this.sessionTokenHash.equals(tokenHash)) {
            this.sessionTakeovers++;
        }
        this.sessionTokenHash = tokenHash;
        this.sessionIssuedAt = issuedAt;
        this.sessionExpiresAt = expiresAt;
        this.lastSeenAt = issuedAt;
    }

    /** True when the bound session is still inside its validity window. */
    public boolean hasLiveSession(Instant now) {
        return sessionTokenHash != null
                && sessionExpiresAt != null
                && now.isBefore(sessionExpiresAt);
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    // -------------------------------------------------------------- lifecycle

    /**
     * Record a tentative language choice.
     *
     * <p>Legal only while INITIALIZED. The caller checks that; this method is the
     * recording of the decision, not the decision.
     */
    public void chooseLanguage(ArenaLanguage chosen) {
        this.language = chosen.id();
    }

    public ArenaLanguage languageOrNull() {
        return language == null ? null : ArenaLanguage.fromId(language).orElse(null);
    }

    /**
     * The state a client should act on.
     *
     * <p>An ACTIVE attempt whose deadline has passed reports EXPIRED, derived from
     * the clock rather than stored, so it can never contradict it. Exactly the shape
     * of {@code Event.effectiveRegistrationStatus()}, and for the same reason: a
     * derived answer cannot drift from the thing it is derived from, and there is no
     * scheduled job standing between the truth and the screen.
     *
     * <p>Phase E persists the transition and scores it. Until then this is what
     * keeps a lapsed attempt from rendering a negative countdown.
     */
    public AttemptState effectiveState(Instant now) {
        if (state == AttemptState.ACTIVE && expiresAt != null && !now.isBefore(expiresAt)) {
            return AttemptState.EXPIRED;
        }
        return state;
    }

    /** Whole seconds left, floored at zero. Zero whenever the mission is not running. */
    public long remainingSeconds(Instant now) {
        if (state != AttemptState.ACTIVE || expiresAt == null) {
            return 0L;
        }
        long seconds = Duration.between(now, expiresAt).getSeconds();
        return Math.max(seconds, 0L);
    }

    // --------------------------------------------------------------- accessors

    public Long getId() {
        return id;
    }

    public Registration getRegistration() {
        return registration;
    }

    public Participant getParticipant() {
        return participant;
    }

    public AttemptState getState() {
        return state;
    }

    public String getLanguage() {
        return language;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public int getSessionTakeovers() {
        return sessionTakeovers;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Integer getScore() {
        return score;
    }

    /** Set from the sum of the per-problem awards. Never from a request. */
    public void setScore(Integer score) {
        this.score = score;
    }

    public String getFinalStateReason() {
        return finalStateReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
