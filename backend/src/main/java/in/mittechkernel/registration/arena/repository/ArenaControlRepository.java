package in.mittechkernel.registration.arena.repository;

import in.mittechkernel.registration.arena.entity.ArenaControl;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Arena lifecycle rows, keyed by event id.
 *
 * <p>Seeded by V12 for every event with {@code requires_access_code}, so the row for
 * a configured arena always exists.
 */
public interface ArenaControlRepository extends JpaRepository<ArenaControl, String> {
}
