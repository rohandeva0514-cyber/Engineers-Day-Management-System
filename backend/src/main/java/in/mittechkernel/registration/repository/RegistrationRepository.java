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
}
