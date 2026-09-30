package in.mittechkernel.registration.arena.repository;

import in.mittechkernel.registration.arena.entity.AttemptProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttemptProblemRepository extends JpaRepository<AttemptProblem, Long> {

    /** The board, in a fixed order so it cannot reshuffle between requests. */
    @Query("""
            SELECT ap FROM AttemptProblem ap
             WHERE ap.attempt.id = :attemptId
             ORDER BY ap.difficulty ASC, ap.ordinal ASC
            """)
    List<AttemptProblem> findBoard(@Param("attemptId") Long attemptId);

    /**
     * One slot, scoped to the attempt.
     *
     * <p>The attempt id is part of the query, not checked afterwards. A participant
     * can only ever name a {@code ref}, and a ref is meaningless without the attempt
     * it belongs to - so reaching another participant's row is not something this
     * method can express, rather than something a caller must remember to prevent.
     */
    @Query("""
            SELECT ap FROM AttemptProblem ap
             WHERE ap.attempt.id = :attemptId AND ap.ref = :ref
            """)
    Optional<AttemptProblem> findSlot(@Param("attemptId") Long attemptId,
                                      @Param("ref") String ref);

    @Query("SELECT count(ap) FROM AttemptProblem ap WHERE ap.attempt.id = :attemptId")
    long countForAttempt(@Param("attemptId") Long attemptId);

    @Query("""
            SELECT count(ap) FROM AttemptProblem ap
             WHERE ap.attempt.id = :attemptId
               AND ap.status = in.mittechkernel.registration.arena.entity.AttemptProblemStatus.SOLVED
            """)
    long countSolved(@Param("attemptId") Long attemptId);
}
