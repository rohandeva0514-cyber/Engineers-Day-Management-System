package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /** Team names are unique per event, case-insensitively. */
    @Query("""
           SELECT COUNT(t) > 0 FROM Team t
            WHERE t.event.id = :eventId AND lower(t.name) = lower(:name)
           """)
    boolean existsByEventAndNameIgnoreCase(@Param("eventId") String eventId,
                                           @Param("name") String name);
}
