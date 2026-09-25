package in.mittechkernel.registration.repository;

import in.mittechkernel.registration.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /**
     * The identity lookup.
     *
     * <p>Email is unique case-insensitively, so this is the only lookup that can
     * return "the" participant. Roll number cannot: two students may share one.
     */
    @Query("SELECT p FROM Participant p WHERE lower(p.email) = lower(:email)")
    Optional<Participant> findByEmailIgnoreCase(@Param("email") String email);

    /** Resolves a whole roster in one query. Emails are passed already lower-cased. */
    @Query("SELECT p FROM Participant p WHERE lower(p.email) IN :emails")
    List<Participant> findAllByEmailInIgnoreCase(@Param("emails") List<String> emails);

    /**
     * Everyone holding this roll number.
     *
     * <p>Returns a list, and deliberately so: roll numbers repeat, and a method
     * handing back a single Optional here would be a lie that throws the first
     * time two students share one.
     */
    List<Participant> findAllByRollNo(String rollNo);
}
