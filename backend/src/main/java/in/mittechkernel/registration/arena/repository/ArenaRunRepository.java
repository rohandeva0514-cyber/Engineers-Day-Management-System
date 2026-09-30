package in.mittechkernel.registration.arena.repository;

import in.mittechkernel.registration.arena.entity.ArenaRun;
import org.springframework.data.jpa.repository.JpaRepository;

/** Execution history. Admin-facing; nothing here is projected to a participant. */
public interface ArenaRunRepository extends JpaRepository<ArenaRun, Long> {
}
