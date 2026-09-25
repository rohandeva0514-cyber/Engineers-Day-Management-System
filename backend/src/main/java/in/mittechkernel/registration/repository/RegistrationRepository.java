package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /**
     * Fast-path duplicate check. The authoritative guard is the
     * UNIQUE (event_id, participant_id) constraint - this only exists so the common case
     * returns a clear 409 instead of surfacing a constraint violation.
     */
    @Query("""
           SELECT r.participant.rollNo FROM Registration r
            WHERE r.event.id = :eventId AND r.participant.id IN :participantIds
           """)
    List<String> findAlreadyRegisteredRollNos(@Param("eventId") String eventId,
                                              @Param("participantIds") List<Long> participantIds);

    @Query("""
           SELECT r FROM Registration r
             JOIN FETCH r.event
             JOIN FETCH r.participant
            WHERE r.participant.id = :participantId
            ORDER BY r.createdAt ASC
           """)
    List<Registration> findAllByParticipantId(@Param("participantId") Long participantId);

    /** Returns an existing registration for a replayed idempotency key, if any. */
    @Query("""
           SELECT r FROM Registration r
            WHERE r.event.id = :eventId AND r.idempotencyKey = :key
            ORDER BY r.id ASC
            LIMIT 1
           """)
    Optional<Registration> findByEventIdAndIdempotencyKey(@Param("eventId") String eventId,
                                                          @Param("key") String key);

    @Query("SELECT r FROM Registration r WHERE r.team.id = :teamId ORDER BY r.id ASC")
    List<Registration> findAllByTeamId(@Param("teamId") Long teamId);

    /**
     * Resolve an event-day access code.
     *
     * <p>Joins the event and participant because the verification response needs
     * both, and a lazy load here would fire two more queries per check-in.
     */
    @Query("""
            SELECT r FROM Registration r
              JOIN FETCH r.participant
              JOIN FETCH r.event
             WHERE r.accessCode = :code
            """)
    Optional<Registration> findByAccessCode(@Param("code") String code);

    /** Live registration count per event, for the admin dashboard. */
    @Query("SELECT r.event.id, COUNT(r) FROM Registration r GROUP BY r.event.id")
    List<Object[]> countByEvent();

    /**
     * The admin participant list: every registration, with its student, filtered.
     *
     * <p>Every filter is optional and applied as "null means no filter", so one query
     * serves the whole screen rather than a combinatorial set of finder methods. The
     * search term is matched case-insensitively against name, email and roll number,
     * which is what an organiser at a desk actually types.
     *
     * <p>Joins are fetched so rendering a page does not fire a query per row.
     *
     * <p>Every parameter is CAST explicitly. Without it PostgreSQL cannot infer the
     * type of a null bind and falls back to bytea, so an unfiltered search fails with
     * "function lower(bytea) does not exist" - a runtime error that only appears when
     * a filter is left blank, which is the normal case.
     */
    @Query("""
            SELECT r FROM Registration r
              JOIN FETCH r.participant p
              JOIN FETCH r.event e
              LEFT JOIN FETCH r.team t
             WHERE (cast(:eventId  as string) IS NULL OR e.id = :eventId)
               AND (cast(:year     as short)  IS NULL OR p.yearLevel = :year)
               AND (cast(:branch   as string) IS NULL OR p.branch = :branch)
               AND (cast(:division as string) IS NULL OR p.division = :division)
               AND (cast(:search   as string) IS NULL
                    OR lower(p.fullName) LIKE lower(concat('%', cast(:search as string), '%'))
                    OR lower(p.email)    LIKE lower(concat('%', cast(:search as string), '%'))
                    OR lower(p.rollNo)   LIKE lower(concat('%', cast(:search as string), '%')))
             ORDER BY r.id ASC
            """)
    List<Registration> findForAdmin(@Param("eventId") String eventId,
                                    @Param("year") Short year,
                                    @Param("branch") String branch,
                                    @Param("division") String division,
                                    @Param("search") String search);
}
