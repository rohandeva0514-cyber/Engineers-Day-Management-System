package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findAllByOrderByDisplayOrderAsc();

    /**
     * Atomically claim {@code seats} seats, or claim nothing.
     *
     * <p>This single statement is the whole capacity mechanism. It is a conditional UPDATE,
     * not a read-then-write: PostgreSQL evaluates the predicate and applies the increment
     * under one row lock, so concurrent callers are serialised on the event row and the
     * check can never be stale by the time the increment lands.
     *
     * <p>The classic bug this avoids is
     * {@code SELECT count(*) ... ; if (count < 30) INSERT}, where fifty simultaneous
     * requests all read 29 and all insert. Here, exactly {@code capacity} callers get a
     * row count of 1 and the rest get 0.
     *
     * <p>{@code capacity IS NULL} means the event has no automatic limit (manual closure),
     * and every claim succeeds.
     *
     * @return 1 if the seats were claimed, 0 if that would have exceeded capacity
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE event
               SET seats_taken = seats_taken + :seats,
                   updated_at  = now()
             WHERE id = :eventId
               AND (capacity IS NULL OR seats_taken + :seats <= capacity)
            """, nativeQuery = true)
    int tryClaimSeats(@Param("eventId") String eventId, @Param("seats") int seats);

    /**
     * Release previously claimed seats. Guarded the same way so the counter can never go
     * negative even if called twice.
     *
     * <p>Not reachable from the API in this milestone - there is no withdrawal endpoint yet -
     * but the seat counter is only trustworthy if the release path exists and is correct
     * from the start.
     *
     * @return 1 if the seats were released, 0 otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE event
               SET seats_taken = seats_taken - :seats,
                   updated_at  = now()
             WHERE id = :eventId
               AND seats_taken >= :seats
            """, nativeQuery = true)
    int releaseSeats(@Param("eventId") String eventId, @Param("seats") int seats);
}
