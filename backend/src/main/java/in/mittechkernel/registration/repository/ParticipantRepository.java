package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByRollNo(String rollNo);

    List<Participant> findAllByRollNoIn(List<String> rollNos);

    /** Email is unique case-insensitively, so lookups must be too. */
    @Query("SELECT p FROM Participant p WHERE lower(p.email) = lower(:email)")
    Optional<Participant> findByEmailIgnoreCase(@Param("email") String email);
}
