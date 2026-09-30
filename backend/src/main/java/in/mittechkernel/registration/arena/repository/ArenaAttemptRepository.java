package in.mittechkernel.registration.arena.repository;

import in.mittechkernel.registration.arena.entity.ArenaAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArenaAttemptRepository extends JpaRepository<ArenaAttempt, Long> {

    /**
     * Resolve a session token hash.
     *
     * <p>Runs on every protected arena request, so the joins are fetched: the
     * participant and event are needed to answer almost any of them, and a lazy load
     * here would fire extra queries per keystroke once drafts start saving.
     */
    @Query("""
            SELECT a FROM ArenaAttempt a
              JOIN FETCH a.participant
              JOIN FETCH a.registration r
              JOIN FETCH r.event
             WHERE a.sessionTokenHash = :hash
            """)
    Optional<ArenaAttempt> findBySessionTokenHash(@Param("hash") String hash);

    @Query("""
            SELECT a FROM ArenaAttempt a
              JOIN FETCH a.participant
              JOIN FETCH a.registration r
              JOIN FETCH r.event
             WHERE r.id = :registrationId
            """)
    Optional<ArenaAttempt> findByRegistrationId(@Param("registrationId") Long registrationId);

    /**
     * Load an attempt by id with everything a response needs already attached.
     *
     * <p>{@code findById} is not enough. The application runs with
     * {@code open-in-view: false}, so there is no session left once a controller
     * starts serialising - and every arena response names the participant and the
     * event. Fetching them here is what keeps that a single query instead of a
     * LazyInitializationException.
     */
    @Query("""
            SELECT a FROM ArenaAttempt a
              JOIN FETCH a.participant
              JOIN FETCH a.registration r
              JOIN FETCH r.event
             WHERE a.id = :id
            """)
    Optional<ArenaAttempt> findByIdWithDetail(@Param("id") Long id);

    /**
     * Start the mission, atomically.
     *
     * <p>A conditional UPDATE rather than read-modify-write, and for the same reason
     * {@code EventRepository.tryClaimSeats} is one: two requests arriving together -
     * a double-click, or a retry on a flaky network - would both read INITIALIZED and
     * both write ACTIVE, and the second would silently restart the clock. The
     * {@code AND state = 'INITIALIZED'} is evaluated under the row lock the UPDATE
     * itself takes, so exactly one caller gets a row count of 1.
     *
     * <p>Returns the number of rows changed: 1 for the caller that started it, 0 for
     * everyone else. A 0 is not an error by itself - the caller re-reads and decides
     * whether it means "already running" or "already finished".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ArenaAttempt a
               SET a.state = in.mittechkernel.registration.arena.entity.AttemptState.ACTIVE,
                   a.language = :language,
                   a.startedAt = :startedAt,
                   a.expiresAt = :expiresAt,
                   a.sessionExpiresAt = :sessionExpiresAt,
                   a.lastSeenAt = :startedAt
             WHERE a.id = :id
               AND a.state = in.mittechkernel.registration.arena.entity.AttemptState.INITIALIZED
            """)
    int tryStart(@Param("id") Long id,
                 @Param("language") String language,
                 @Param("startedAt") Instant startedAt,
                 @Param("expiresAt") Instant expiresAt,
                 @Param("sessionExpiresAt") Instant sessionExpiresAt);

    /**
     * Finalise the mission, atomically and exactly once.
     *
     * <p>A conditional UPDATE for the same reason {@code tryStart} is one: two
     * requests arriving together - a double-click on the confirm button, or a retry
     * on a flaky hall network - would both read ACTIVE and both write SUBMITTED, and
     * the second would overwrite the first submission's timestamp.
     *
     * <p>The deadline is part of the condition rather than a check beforehand, so it
     * is evaluated under the row lock the UPDATE itself takes. There is no window in
     * which a submission that was in time when it was validated becomes late before
     * it is written, and no way for a client to influence it.
     *
     * <p>Returns 1 for the caller that finalised it and 0 for everyone else. A 0 is
     * not an error by itself - the caller re-reads and decides whether it means
     * "already submitted", "out of time", or "never started".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ArenaAttempt a
               SET a.state = in.mittechkernel.registration.arena.entity.AttemptState.SUBMITTED,
                   a.finalizedAt = :now,
                   a.finalStateReason = 'PARTICIPANT',
                   a.lastSeenAt = :now
             WHERE a.id = :id
               AND a.state = in.mittechkernel.registration.arena.entity.AttemptState.ACTIVE
               AND a.expiresAt > :now
            """)
    int tryFinalize(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Submissions an organiser needs to evaluate.
     *
     * <p>Deliberately NOT just SUBMITTED. A participant whose clock ran out still has
     * their autosaved code and still has to be marked, so anything past INITIALIZED
     * is listed and the state is shown so an organiser can tell the two apart. The
     * alternative would be silently excluding everyone who ran out of time.
     */
    @Query("""
            SELECT a FROM ArenaAttempt a
              JOIN FETCH a.participant
              JOIN FETCH a.registration r
              JOIN FETCH r.event
             WHERE a.state <> in.mittechkernel.registration.arena.entity.AttemptState.INITIALIZED
             ORDER BY a.finalizedAt ASC NULLS LAST, a.id ASC
            """)
    List<ArenaAttempt> findForEvaluation();
}
